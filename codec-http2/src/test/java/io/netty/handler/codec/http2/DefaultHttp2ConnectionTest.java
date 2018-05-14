/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.handler.codec.http2.Http2Connection.Endpoint;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.Integer.MAX_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link DefaultHttp2Connection}.
 */
public class DefaultHttp2ConnectionTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DefaultHttp2Connection server;
    private DefaultHttp2Connection client;
    private static DefaultEventLoopGroup group;

    @Mock
    private Http2Connection.Listener clientListener;

    @Mock
    private Http2Connection.Listener clientListener2;

    @BeforeClass
    public static void beforeClass() {
        group = new DefaultEventLoopGroup(2);
    }

    @AfterClass
    public static void afterClass() {
        group.shutdownGracefully();
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        server = new DefaultHttp2Connection(true);
        client = new DefaultHttp2Connection(false);
        client.addListener(clientListener);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                assertNotNull(client.stream(((Http2Stream) invocation.getArgument(0)).id()));
                return null;
            }
        }).when(clientListener).onStreamClosed(any(Http2Stream.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                assertNull(client.stream(((Http2Stream) invocation.getArgument(0)).id()));
                return null;
            }
        }).when(clientListener).onStreamRemoved(any(Http2Stream.class));
    }

    @Test
    public void getStreamWithoutStreamShouldReturnNull() {
        assertNull(server.stream(100));
    }

    @Test
    public void removeAllStreamsWithEmptyStreams() throws InterruptedException {
        testRemoveAllStreams();
    }

    @Test
    public void removeAllStreamsWithJustOneLocalStream() throws InterruptedException, Http2Exception {
        client.local().createStream(3, false);
        testRemoveAllStreams();
    }

    @Test
    public void removeAllStreamsWithJustOneRemoveStream() throws InterruptedException, Http2Exception {
        client.remote().createStream(2, false);
        testRemoveAllStreams();
    }

    @Test
    public void removeAllStreamsWithManyActiveStreams() throws InterruptedException, Http2Exception {
        Endpoint<Http2RemoteFlowController> remote = client.remote();
        Endpoint<Http2LocalFlowController> local = client.local();
        for (int c = 3, s = 2; c < 5000; c += 2, s += 2) {
            local.createStream(c, false);
            remote.createStream(s, false);
        }
        testRemoveAllStreams();
    }

    @Test
    public void removeIndividualStreamsWhileCloseDoesNotNPE() throws InterruptedException, Http2Exception {
        final Http2Stream streamA = client.local().createStream(3, false);
        final Http2Stream streamB = client.remote().createStream(2, false);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                streamA.close();
                streamB.close();
                return null;
            }
        }).when(clientListener2).onStreamClosed(any(Http2Stream.class));
        try {
            client.addListener(clientListener2);
            testRemoveAllStreams();
        } finally {
            client.removeListener(clientListener2);
        }
    }

    @Test
    public void removeAllStreamsWhileIteratingActiveStreams() throws InterruptedException, Http2Exception {
        final Endpoint<Http2RemoteFlowController> remote = client.remote();
        final Endpoint<Http2LocalFlowController> local = client.local();
        for (int c = 3, s = 2; c < 5000; c += 2, s += 2) {
            local.createStream(c, false);
            remote.createStream(s, false);
        }
        final Promise<Void> promise = group.next().newPromise();
        final CountDownLatch latch = new CountDownLatch(client.numActiveStreams());
        client.forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) {
                client.close(promise).addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        assertTrue(promise.isDone());
                        latch.countDown();
                    }
                });
                return true;
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void removeAllStreamsWhileIteratingActiveStreamsAndExceptionOccurs()
            throws InterruptedException, Http2Exception {
        final Endpoint<Http2RemoteFlowController> remote = client.remote();
        final Endpoint<Http2LocalFlowController> local = client.local();
        for (int c = 3, s = 2; c < 5000; c += 2, s += 2) {
            local.createStream(c, false);
            remote.createStream(s, false);
        }
        final Promise<Void> promise = group.next().newPromise();
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            client.forEachActiveStream(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) throws Http2Exception {
                    // This close call is basically a noop, because the following statement will throw an exception.
                    client.close(promise);
                    // Do an invalid operation while iterating.
                    remote.createStream(3, false);
                    return true;
                }
            });
        } catch (Http2Exception ignored) {
            client.close(promise).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    assertTrue(promise.isDone());
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void goAwayReceivedShouldCloseStreamsGreaterThanLastStream() throws Exception {
        Http2Stream stream1 = client.local().createStream(3, false);
        Http2Stream stream2 = client.local().createStream(5, false);
        Http2Stream remoteStream = client.remote().createStream(4, false);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.OPEN, stream2.state());

        client.goAwayReceived(3, 8, null);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.CLOSED, stream2.state());
        assertEquals(State.OPEN, remoteStream.state());
        assertEquals(3, client.local().lastStreamKnownByPeer());
        assertEquals(5, client.local().lastStreamCreated());
        // The remote endpoint must not be affected by a received GOAWAY frame.
        assertEquals(-1, client.remote().lastStreamKnownByPeer());
        assertEquals(State.OPEN, remoteStream.state());
    }

    @Test
    public void goAwaySentShouldCloseStreamsGreaterThanLastStream() throws Exception {
        Http2Stream stream1 = server.remote().createStream(3, false);
        Http2Stream stream2 = server.remote().createStream(5, false);
        Http2Stream localStream = server.local().createStream(4, false);

        server.goAwaySent(3, 8, null);

        assertEquals(State.OPEN, stream1.state());
        assertEquals(State.CLOSED, stream2.state());

        assertEquals(3, server.remote().lastStreamKnownByPeer());
        assertEquals(5, server.remote().lastStreamCreated());
        // The local endpoint must not be affected by a sent GOAWAY frame.
        assertEquals(-1, server.local().lastStreamKnownByPeer());
        assertEquals(State.OPEN, localStream.state());
    }

    @Test
    public void serverCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.local().createStream(2, false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(2, server.local().lastStreamCreated());

        stream = server.local().createStream(4, true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(2, server.numActiveStreams());
        assertEquals(4, server.local().lastStreamCreated());

        stream = server.remote().createStream(3, true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(3, server.numActiveStreams());
        assertEquals(3, server.remote().lastStreamCreated());

        stream = server.remote().createStream(5, false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, server.numActiveStreams());
        assertEquals(5, server.remote().lastStreamCreated());
    }

    @Test
    public void clientCreateStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = client.remote().createStream(2, false);
        assertEquals(2, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(1, client.numActiveStreams());
        assertEquals(2, client.remote().lastStreamCreated());

        stream = client.remote().createStream(4, true);
        assertEquals(4, stream.id());
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(2, client.numActiveStreams());
        assertEquals(4, client.remote().lastStreamCreated());
        assertTrue(stream.isHeadersReceived());

        stream = client.local().createStream(3, true);
        assertEquals(3, stream.id());
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(3, client.numActiveStreams());
        assertEquals(3, client.local().lastStreamCreated());
        assertTrue(stream.isHeadersSent());

        stream = client.local().createStream(5, false);
        assertEquals(5, stream.id());
        assertEquals(State.OPEN, stream.state());
        assertEquals(4, client.numActiveStreams());
        assertEquals(5, client.local().lastStreamCreated());
    }

    @Test
    public void serverReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        Http2Stream pushStream = server.local().reservePushStream(2, stream);
        assertEquals(2, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(2, server.local().lastStreamCreated());
    }

    @Test
    public void clientReservePushStreamShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        Http2Stream pushStream = server.local().reservePushStream(4, stream);
        assertEquals(4, pushStream.id());
        assertEquals(State.RESERVED_LOCAL, pushStream.state());
        assertEquals(1, server.numActiveStreams());
        assertEquals(4, server.local().lastStreamCreated());
    }

    @Test
    public void serverRemoteIncrementAndGetStreamShouldSucceed() throws Http2Exception {
        incrementAndGetStreamShouldSucceed(server.remote());
    }

    @Test
    public void serverLocalIncrementAndGetStreamShouldSucceed() throws Http2Exception {
        incrementAndGetStreamShouldSucceed(server.local());
    }

    @Test
    public void clientRemoteIncrementAndGetStreamShouldSucceed() throws Http2Exception {
        incrementAndGetStreamShouldSucceed(client.remote());
    }

    @Test
    public void clientLocalIncrementAndGetStreamShouldSucceed() throws Http2Exception {
        incrementAndGetStreamShouldSucceed(client.local());
    }

    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void serverRemoteIncrementAndGetStreamShouldRespectOverflow() throws Http2Exception {
        incrementAndGetStreamShouldRespectOverflow(server.remote(), MAX_VALUE);
    }

    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void serverLocalIncrementAndGetStreamShouldRespectOverflow() throws Http2Exception {
        incrementAndGetStreamShouldRespectOverflow(server.local(), MAX_VALUE - 1);
    }

    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void clientRemoteIncrementAndGetStreamShouldRespectOverflow() throws Http2Exception {
        incrementAndGetStreamShouldRespectOverflow(client.remote(), MAX_VALUE - 1);
    }

    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void clientLocalIncrementAndGetStreamShouldRespectOverflow() throws Http2Exception {
        incrementAndGetStreamShouldRespectOverflow(client.local(), MAX_VALUE);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamBehindExpectedShouldThrow() throws Http2Exception {
        server.local().createStream(0, true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForServerShouldThrow() throws Http2Exception {
        server.local().createStream(11, true);
    }

    @Test(expected = Http2Exception.class)
    public void newStreamNotForClientShouldThrow() throws Http2Exception {
        client.local().createStream(10, true);
    }

    @Test(expected = Http2Exception.class)
    public void createShouldThrowWhenMaxAllowedStreamsOpenExceeded() throws Http2Exception {
        server.local().maxActiveStreams(0);
        server.local().createStream(2, true);
    }

    @Test(expected = Http2Exception.class)
    public void serverCreatePushShouldFailOnRemoteEndpointWhenMaxAllowedStreamsExceeded() throws Http2Exception {
        server = new DefaultHttp2Connection(true, 0);
        server.remote().maxActiveStreams(1);
        Http2Stream requestStream = server.remote().createStream(3, false);
        server.remote().reservePushStream(2, requestStream);
    }

    @Test(expected = Http2Exception.class)
    public void clientCreatePushShouldFailOnRemoteEndpointWhenMaxAllowedStreamsExceeded() throws Http2Exception {
        client = new DefaultHttp2Connection(false, 0);
        client.remote().maxActiveStreams(1);
        Http2Stream requestStream = client.remote().createStream(2, false);
        client.remote().reservePushStream(4, requestStream);
    }

    @Test
    public void serverCreatePushShouldSucceedOnLocalEndpointWhenMaxAllowedStreamsExceeded() throws Http2Exception {
        server = new DefaultHttp2Connection(true, 0);
        server.local().maxActiveStreams(1);
        Http2Stream requestStream = server.remote().createStream(3, false);
        assertNotNull(server.local().reservePushStream(2, requestStream));
    }

    @Test(expected = Http2Exception.class)
    public void reserveWithPushDisallowedShouldThrow() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        server.remote().allowPushTo(false);
        server.local().reservePushStream(2, stream);
    }

    @Test(expected = Http2Exception.class)
    public void goAwayReceivedShouldDisallowCreation() throws Http2Exception {
        server.goAwayReceived(0, 1L, Unpooled.EMPTY_BUFFER);
        server.remote().createStream(3, true);
    }

    @Test
    public void closeShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        stream.close();
        assertEquals(State.CLOSED, stream.state());
        assertEquals(0, server.numActiveStreams());
    }

    @Test
    public void closeLocalWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, false);
        stream.closeLocalSide();
        assertEquals(State.HALF_CLOSED_LOCAL, stream.state());
        assertEquals(1, server.numActiveStreams());
    }

    @Test
    public void closeRemoteWhenOpenShouldSucceed() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, false);
        stream.closeRemoteSide();
        assertEquals(State.HALF_CLOSED_REMOTE, stream.state());
        assertEquals(1, server.numActiveStreams());
    }

    @Test
    public void closeOnlyOpenSideShouldClose() throws Http2Exception {
        Http2Stream stream = server.remote().createStream(3, true);
        stream.closeLocalSide();
        assertEquals(State.CLOSED, stream.state());
        assertEquals(0, server.numActiveStreams());
    }

    @SuppressWarnings("NumericOverflow")
    @Test(expected = Http2Exception.class)
    public void localStreamInvalidStreamIdShouldThrow() throws Http2Exception {
        client.local().createStream(MAX_VALUE + 2, false);
    }

    @SuppressWarnings("NumericOverflow")
    @Test(expected = Http2Exception.class)
    public void remoteStreamInvalidStreamIdShouldThrow() throws Http2Exception {
        client.remote().createStream(MAX_VALUE + 1, false);
    }

    /**
     * We force {@link #clientListener} methods to all throw a {@link RuntimeException} and verify the following:
     * <ol>
     * <li>all listener methods are called for both {@link #clientListener} and {@link #clientListener2}</li>
     * <li>{@link #clientListener2} is notified after {@link #clientListener}</li>
     * <li>{@link #clientListener2} methods are all still called despite {@link #clientListener}'s
     * method throwing a {@link RuntimeException}</li>
     * </ol>
     */
    @Test
    public void listenerThrowShouldNotPreventOtherListenersFromBeingNotified() throws Http2Exception {
        final boolean[] calledArray = new boolean[128];
        // The following setup will ensure that clientListener throws exceptions, and marks a value in an array
        // such that clientListener2 will verify that is is set or fail the test.
        int methodIndex = 0;
        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamAdded(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamAdded(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamActive(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamActive(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamHalfClosed(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamHalfClosed(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamClosed(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamClosed(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamRemoved(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamRemoved(any(Http2Stream.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));

        doAnswer(new ListenerExceptionThrower(calledArray, methodIndex))
            .when(clientListener).onStreamAdded(any(Http2Stream.class));
        doAnswer(new ListenerVerifyCallAnswer(calledArray, methodIndex++))
            .when(clientListener2).onStreamAdded(any(Http2Stream.class));

        // Now we add clientListener2 and exercise all listener functionality
        try {
            client.addListener(clientListener2);
            Http2Stream stream = client.local().createStream(3, false);
            verify(clientListener).onStreamAdded(any(Http2Stream.class));
            verify(clientListener2).onStreamAdded(any(Http2Stream.class));
            verify(clientListener).onStreamActive(any(Http2Stream.class));
            verify(clientListener2).onStreamActive(any(Http2Stream.class));

            Http2Stream reservedStream = client.remote().reservePushStream(2, stream);
            verify(clientListener, never()).onStreamActive(streamEq(reservedStream));
            verify(clientListener2, never()).onStreamActive(streamEq(reservedStream));

            reservedStream.open(false);
            verify(clientListener).onStreamActive(streamEq(reservedStream));
            verify(clientListener2).onStreamActive(streamEq(reservedStream));

            stream.closeLocalSide();
            verify(clientListener).onStreamHalfClosed(any(Http2Stream.class));
            verify(clientListener2).onStreamHalfClosed(any(Http2Stream.class));

            stream.close();
            verify(clientListener).onStreamClosed(any(Http2Stream.class));
            verify(clientListener2).onStreamClosed(any(Http2Stream.class));
            verify(clientListener).onStreamRemoved(any(Http2Stream.class));
            verify(clientListener2).onStreamRemoved(any(Http2Stream.class));

            client.goAwaySent(client.connectionStream().id(), Http2Error.INTERNAL_ERROR.code(), Unpooled.EMPTY_BUFFER);
            verify(clientListener).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));
            verify(clientListener2).onGoAwaySent(anyInt(), anyLong(), any(ByteBuf.class));

            client.goAwayReceived(client.connectionStream().id(),
                    Http2Error.INTERNAL_ERROR.code(), Unpooled.EMPTY_BUFFER);
            verify(clientListener).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
            verify(clientListener2).onGoAwayReceived(anyInt(), anyLong(), any(ByteBuf.class));
        } finally {
            client.removeListener(clientListener2);
        }
    }

    private void testRemoveAllStreams() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Promise<Void> promise = group.next().newPromise();
        client.close(promise).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                assertTrue(promise.isDone());
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private static void incrementAndGetStreamShouldRespectOverflow(Endpoint<?> endpoint, int streamId)
            throws Http2Exception {
        assertTrue(streamId > 0);
        try {
            endpoint.createStream(streamId, true);
            streamId = endpoint.incrementAndGetNextStreamId();
        } catch (Throwable t) {
            fail();
        }
        assertTrue(streamId < 0);
        endpoint.createStream(streamId, true);
    }

    private static void incrementAndGetStreamShouldSucceed(Endpoint<?> endpoint) throws Http2Exception {
        Http2Stream streamA = endpoint.createStream(endpoint.incrementAndGetNextStreamId(), true);
        Http2Stream streamB = endpoint.createStream(streamA.id() + 2, true);
        Http2Stream streamC = endpoint.createStream(endpoint.incrementAndGetNextStreamId(), true);
        assertEquals(streamB.id() + 2, streamC.id());
        endpoint.createStream(streamC.id() + 2, true);
    }

    private static final class ListenerExceptionThrower implements Answer<Void> {
        private static final RuntimeException FAKE_EXCEPTION = new RuntimeException("Fake Exception");
        private final boolean[] array;
        private final int index;

        public ListenerExceptionThrower(boolean[] array, int index) {
            this.array = array;
            this.index = index;
        }

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            array[index] = true;
            throw FAKE_EXCEPTION;
        }
    }

    private static final class ListenerVerifyCallAnswer implements Answer<Void> {
        private final boolean[] array;
        private final int index;

        public ListenerVerifyCallAnswer(boolean[] array, int index) {
            this.array = array;
            this.index = index;
        }

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            assertTrue(array[index]);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T streamEq(T stream) {
        return (T) (stream == null ? ArgumentMatchers.<Http2Stream>isNull() : eq(stream));
    }
}
