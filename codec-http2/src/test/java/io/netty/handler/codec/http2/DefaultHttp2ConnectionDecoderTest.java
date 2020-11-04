/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.CharsetUtil.UTF_8;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultHttp2ConnectionDecoder}.
 */
public class DefaultHttp2ConnectionDecoderTest {
    private static final int STREAM_ID = 3;
    private static final int PUSH_STREAM_ID = 2;
    private static final int STREAM_DEPENDENCY_ID = 5;
    private static final int STATE_RECV_HEADERS = 1;
    private static final int STATE_RECV_TRAILERS = 1 << 1;

    private Http2ConnectionDecoder decoder;
    private ChannelPromise promise;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint<Http2RemoteFlowController> remote;

    @Mock
    private Http2Connection.Endpoint<Http2LocalFlowController> local;

    @Mock
    private Http2LocalFlowController localFlow;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2Stream pushStream;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private Http2FrameReader reader;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private Http2ConnectionEncoder encoder;

    @Mock
    private Http2LifecycleManager lifecycleManager;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel);

        final AtomicInteger headersReceivedState = new AtomicInteger();
        when(channel.isActive()).thenReturn(true);
        when(stream.id()).thenReturn(STREAM_ID);
        when(stream.state()).thenReturn(OPEN);
        when(stream.open(anyBoolean())).thenReturn(stream);
        when(pushStream.id()).thenReturn(PUSH_STREAM_ID);
        doAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock in) throws Throwable {
                return (headersReceivedState.get() & STATE_RECV_HEADERS) != 0;
            }
        }).when(stream).isHeadersReceived();
        doAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock in) throws Throwable {
                return (headersReceivedState.get() & STATE_RECV_TRAILERS) != 0;
            }
        }).when(stream).isTrailersReceived();
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                boolean isInformational = in.getArgument(0);
                if (isInformational) {
                    return stream;
                }
                for (;;) {
                    int current = headersReceivedState.get();
                    int next = current;
                    if ((current & STATE_RECV_HEADERS) != 0) {
                        if ((current & STATE_RECV_TRAILERS) != 0) {
                            throw new IllegalStateException("already sent headers!");
                        }
                        next |= STATE_RECV_TRAILERS;
                    } else {
                        next |= STATE_RECV_HEADERS;
                    }
                    if (headersReceivedState.compareAndSet(current, next)) {
                        break;
                    }
                }
                return stream;
            }
        }).when(stream).headersReceived(anyBoolean());
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                Http2StreamVisitor visitor = in.getArgument(0);
                if (!visitor.visit(stream)) {
                    return stream;
                }
                return null;
            }
        }).when(connection).forEachActiveStream(any(Http2StreamVisitor.class));
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(true);
        when(connection.local()).thenReturn(local);
        when(local.flowController()).thenReturn(localFlow);
        when(encoder.flowController()).thenReturn(remoteFlow);
        when(encoder.frameWriter()).thenReturn(writer);
        when(connection.remote()).thenReturn(remote);
        when(local.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(remote.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);

        decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        decoder.lifecycleManager(lifecycleManager);
        decoder.frameListener(listener);

        // Simulate receiving the initial settings from the remote endpoint.
        decode().onSettingsRead(ctx, new Http2Settings());
        verify(listener).onSettingsRead(eq(ctx), eq(new Http2Settings()));
        assertTrue(decoder.prefaceReceived());
        verify(encoder).writeSettingsAck(eq(ctx), eq(promise));

        // Simulate receiving the SETTINGS ACK for the initial settings.
        decode().onSettingsAckRead(ctx);

        // Disallow any further flushes now that settings ACK has been sent
        when(ctx.flush()).thenThrow(new AssertionFailedError("forbidden"));
    }

    @Test
    public void dataReadAfterGoAwaySentShouldApplyFlowControl() throws Exception {
        mockGoAwaySent();

        final ByteBuf data = dummyData();
        int padding = 10;
        int processedBytes = data.readableBytes() + padding;
        mockFlowControl(processedBytes);
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(padding), eq(true));
            verify(localFlow).consumeBytes(eq(stream), eq(processedBytes));

            // Verify that the event was absorbed and not propagated to the observer.
            verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadAfterGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint() throws Exception {
        mockGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint();

        final ByteBuf data = dummyData();
        int padding = 10;
        int processedBytes = data.readableBytes() + padding;
        mockFlowControl(processedBytes);
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(padding), eq(true));
            verify(localFlow).consumeBytes(eq(stream), eq(processedBytes));

            // Verify that the event was absorbed and not propagated to the observer.
            verify(listener).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test(expected = Http2Exception.StreamException.class)
    public void dataReadForUnknownStreamShouldApplyFlowControlAndFail() throws Exception {
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(true);
        when(connection.stream(STREAM_ID)).thenReturn(null);
        final ByteBuf data = dummyData();
        int padding = 10;
        int processedBytes = data.readableBytes() + padding;
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
        } finally {
            try {
                verify(localFlow)
                        .receiveFlowControlledFrame(eq((Http2Stream) null), eq(data), eq(padding), eq(true));
                verify(localFlow).consumeBytes(eq((Http2Stream) null), eq(processedBytes));
                verify(localFlow).frameWriter(any(Http2FrameWriter.class));
                verifyNoMoreInteractions(localFlow);
                verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
            } finally {
                data.release();
            }
        }
    }

    @Test(expected = Http2Exception.class)
    public void dataReadForUnknownStreamThatCouldntExistFail() throws Exception {
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(false);
        when(connection.stream(STREAM_ID)).thenReturn(null);
        final ByteBuf data = dummyData();
        int padding = 10;
        int processedBytes = data.readableBytes() + padding;
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
        } catch (Http2Exception ex) {
            assertThat(ex, not(instanceOf(Http2Exception.StreamException.class)));
            throw ex;
        } finally {
            try {
                verify(localFlow)
                    .receiveFlowControlledFrame(eq((Http2Stream) null), eq(data), eq(padding), eq(true));
                verify(localFlow).consumeBytes(eq((Http2Stream) null), eq(processedBytes));
                verify(localFlow).frameWriter(any(Http2FrameWriter.class));
                verifyNoMoreInteractions(localFlow);
                verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
            } finally {
                data.release();
            }
        }
    }

    @Test
    public void dataReadForUnknownStreamShouldApplyFlowControl() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        final ByteBuf data = dummyData();
        int padding = 10;
        int processedBytes = data.readableBytes() + padding;
        try {
            try {
                decode().onDataRead(ctx, STREAM_ID, data, padding, true);
                fail();
            } catch (Http2Exception e) {
                verify(localFlow)
                        .receiveFlowControlledFrame(eq((Http2Stream) null), eq(data), eq(padding), eq(true));
                verify(localFlow).consumeBytes(eq((Http2Stream) null), eq(processedBytes));
                verify(localFlow).frameWriter(any(Http2FrameWriter.class));
                verifyNoMoreInteractions(localFlow);

                // Verify that the event was absorbed and not propagated to the observer.
                verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
            }
        } finally {
            data.release();
        }
    }

    @Test
    public void emptyDataFrameShouldApplyFlowControl() throws Exception {
        final ByteBuf data = EMPTY_BUFFER;
        int padding = 0;
        mockFlowControl(0);
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(padding), eq(true));

            // Now we ignore the empty bytes inside consumeBytes method, so it will be called once.
            verify(localFlow).consumeBytes(eq(stream), eq(0));

            // Verify that the empty data event was propagated to the observer.
            verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(padding), eq(true));
        } finally {
            data.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void dataReadForStreamInInvalidStateShouldThrow() throws Exception {
        // Throw an exception when checking stream state.
        when(stream.state()).thenReturn(Http2Stream.State.CLOSED);
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadAfterGoAwaySentForStreamInInvalidStateShouldIgnore() throws Exception {
        // Throw an exception when checking stream state.
        when(stream.state()).thenReturn(Http2Stream.State.CLOSED);
        mockGoAwaySent();
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(10), eq(true));
            verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadAfterGoAwaySentOnUnknownStreamShouldIgnore() throws Exception {
        // Throw an exception when checking stream state.
        when(connection.stream(STREAM_ID)).thenReturn(null);
        mockGoAwaySent();
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(localFlow).receiveFlowControlledFrame((Http2Stream) isNull(), eq(data), eq(10), eq(true));
            verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadAfterRstStreamForStreamInInvalidStateShouldIgnore() throws Exception {
        // Throw an exception when checking stream state.
        when(stream.state()).thenReturn(Http2Stream.State.CLOSED);
        when(stream.isResetSent()).thenReturn(true);
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(10), eq(true));
            verify(listener, never()).onDataRead(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
        } finally {
            data.release();
        }
    }

    @Test
    public void dataReadWithEndOfStreamShouldcloseStreamRemote() throws Exception {
        final ByteBuf data = dummyData();
        try {
            decode().onDataRead(ctx, STREAM_ID, data, 10, true);
            verify(localFlow).receiveFlowControlledFrame(eq(stream), eq(data), eq(10), eq(true));
            verify(lifecycleManager).closeStreamRemote(eq(stream), eq(future));
            verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(10), eq(true));
        } finally {
            data.release();
        }
    }

    @Test
    public void errorDuringDeliveryShouldReturnCorrectNumberOfBytes() throws Exception {
        final ByteBuf data = dummyData();
        final int padding = 10;
        final AtomicInteger unprocessed = new AtomicInteger(data.readableBytes() + padding);
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                return unprocessed.get();
            }
        }).when(localFlow).unconsumedBytes(eq(stream));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                int delta = (Integer) in.getArguments()[1];
                int newValue = unprocessed.addAndGet(-delta);
                if (newValue < 0) {
                    throw new RuntimeException("Returned too many bytes");
                }
                return null;
            }
        }).when(localFlow).consumeBytes(eq(stream), anyInt());
        // When the listener callback is called, process a few bytes and then throw.
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                localFlow.consumeBytes(stream, 4);
                throw new RuntimeException("Fake Exception");
            }
        }).when(listener).onDataRead(eq(ctx), eq(STREAM_ID), any(ByteBuf.class), eq(10), eq(true));
        try {
            decode().onDataRead(ctx, STREAM_ID, data, padding, true);
            fail("Expected exception");
        } catch (RuntimeException cause) {
            verify(localFlow)
                    .receiveFlowControlledFrame(eq(stream), eq(data), eq(padding), eq(true));
            verify(lifecycleManager).closeStreamRemote(eq(stream), eq(future));
            verify(listener).onDataRead(eq(ctx), eq(STREAM_ID), eq(data), eq(padding), eq(true));
            assertEquals(0, localFlow.unconsumedBytes(stream));
        } finally {
            data.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void headersReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
    }

    @Test
    public void headersReadForStreamThatAlreadySentResetShouldBeIgnored() throws Exception {
        when(stream.isResetSent()).thenReturn(true);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(remote, never()).createStream(anyInt(), anyBoolean());
        verify(stream, never()).open(anyBoolean());

        // Verify that the event was absorbed and not propagated to the observer.
        verify(listener, never()).onHeadersRead(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyBoolean());
        verify(remote, never()).createStream(anyInt(), anyBoolean());
        verify(stream, never()).open(anyBoolean());
    }

    @Test
    public void headersReadForUnknownStreamAfterGoAwayShouldBeIgnored() throws Exception {
        mockGoAwaySent();
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(remote, never()).createStream(anyInt(), anyBoolean());
        verify(stream, never()).open(anyBoolean());

        // Verify that the event was absorbed and not propagated to the observer.
        verify(listener, never()).onHeadersRead(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyBoolean());
        verify(remote, never()).createStream(anyInt(), anyBoolean());
        verify(stream, never()).open(anyBoolean());
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateStream() throws Exception {
        final int streamId = 5;
        when(remote.createStream(eq(streamId), anyBoolean())).thenReturn(stream);
        decode().onHeadersRead(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(remote).createStream(eq(streamId), eq(false));
        verify(listener).onHeadersRead(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));
    }

    @Test
    public void headersReadForUnknownStreamShouldCreateHalfClosedStream() throws Exception {
        final int streamId = 5;
        when(remote.createStream(eq(streamId), anyBoolean())).thenReturn(stream);
        decode().onHeadersRead(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, true);
        verify(remote).createStream(eq(streamId), eq(true));
        verify(listener).onHeadersRead(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void headersReadForPromisedStreamShouldHalfOpenStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(stream).open(false);
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false));
    }

    @Test(expected = Http2Exception.class)
    public void trailersDoNotEndStreamThrows() throws Exception {
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        // Trailers must end the stream!
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
    }

    @Test(expected = Http2Exception.class)
    public void tooManyHeadersEOSThrows() throws Exception {
        tooManyHeaderThrows(true);
    }

    @Test(expected = Http2Exception.class)
    public void tooManyHeadersNoEOSThrows() throws Exception {
        tooManyHeaderThrows(false);
    }

    private void tooManyHeaderThrows(boolean eos) throws Exception {
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);
        // We already received the trailers!
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, eos);
    }

    private static Http2Headers informationalHeaders() {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(HttpResponseStatus.CONTINUE.codeAsText());
        return headers;
    }

    @Test
    public void infoHeadersAndTrailersAllowed() throws Exception {
        infoHeadersAndTrailersAllowed(true, 1);
    }

    @Test
    public void multipleInfoHeadersAndTrailersAllowed() throws Exception {
        infoHeadersAndTrailersAllowed(true, 10);
    }

    @Test(expected = Http2Exception.class)
    public void infoHeadersAndTrailersNoEOSThrows() throws Exception {
        infoHeadersAndTrailersAllowed(false, 1);
    }

    @Test(expected = Http2Exception.class)
    public void multipleInfoHeadersAndTrailersNoEOSThrows() throws Exception {
        infoHeadersAndTrailersAllowed(false, 10);
    }

    private void infoHeadersAndTrailersAllowed(boolean eos, int infoHeaderCount) throws Exception {
        for (int i = 0; i < infoHeaderCount; ++i) {
            decode().onHeadersRead(ctx, STREAM_ID, informationalHeaders(), 0, false);
        }
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, eos);
    }

    @Test()
    public void headersReadForPromisedStreamShouldCloseStream() throws Exception {
        when(stream.state()).thenReturn(RESERVED_REMOTE);
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);
        verify(stream).open(true);
        verify(lifecycleManager).closeStreamRemote(eq(stream), eq(future));
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void headersDependencyNotCreatedShouldCreateAndSucceed() throws Exception {
        final short weight = 1;
        decode().onHeadersRead(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, STREAM_DEPENDENCY_ID,
                weight, true, 0, true);
        verify(listener).onHeadersRead(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(STREAM_DEPENDENCY_ID),
                eq(weight), eq(true), eq(0), eq(true));
        verify(remoteFlow).updateDependencyTree(eq(STREAM_ID), eq(STREAM_DEPENDENCY_ID), eq(weight), eq(true));
        verify(lifecycleManager).closeStreamRemote(eq(stream), any(ChannelFuture.class));
    }

    @Test
    public void pushPromiseReadAfterGoAwaySentShouldBeIgnored() throws Exception {
        mockGoAwaySent();
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        verify(remote, never()).reservePushStream(anyInt(), any(Http2Stream.class));
        verify(listener, never()).onPushPromiseRead(eq(ctx), anyInt(), anyInt(), any(Http2Headers.class), anyInt());
    }

    @Test
    public void pushPromiseReadAfterGoAwayShouldAllowFramesForStreamCreatedByLocalEndpoint() throws Exception {
        mockGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint();
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        verify(remote).reservePushStream(anyInt(), any(Http2Stream.class));
        verify(listener).onPushPromiseRead(eq(ctx), anyInt(), anyInt(), any(Http2Headers.class), anyInt());
    }

    @Test(expected = Http2Exception.class)
    public void pushPromiseReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
    }

    @Test
    public void pushPromiseReadShouldSucceed() throws Exception {
        decode().onPushPromiseRead(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        verify(remote).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(listener).onPushPromiseRead(eq(ctx), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EmptyHttp2Headers.INSTANCE), eq(0));
    }

    @Test
    public void priorityReadAfterGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint() throws Exception {
        mockGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint();
        decode().onPriorityRead(ctx, STREAM_ID, 0, (short) 255, true);
        verify(remoteFlow).updateDependencyTree(eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
        verify(listener).onPriorityRead(eq(ctx), anyInt(), anyInt(), anyShort(), anyBoolean());
    }

    @Test
    public void priorityReadForUnknownStreamShouldNotBeIgnored() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onPriorityRead(ctx, STREAM_ID, 0, (short) 255, true);
        verify(remoteFlow).updateDependencyTree(eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
        verify(listener).onPriorityRead(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void priorityReadShouldNotCreateNewStream() throws Exception {
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(false);
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onPriorityRead(ctx, STREAM_ID, STREAM_DEPENDENCY_ID, (short) 255, true);
        verify(remoteFlow).updateDependencyTree(eq(STREAM_ID), eq(STREAM_DEPENDENCY_ID), eq((short) 255), eq(true));
        verify(listener).onPriorityRead(eq(ctx), eq(STREAM_ID), eq(STREAM_DEPENDENCY_ID), eq((short) 255), eq(true));
        verify(remote, never()).createStream(eq(STREAM_ID), anyBoolean());
        verify(stream, never()).open(anyBoolean());
    }

    @Test
    public void windowUpdateReadAfterGoAwaySentShouldBeIgnored() throws Exception {
        mockGoAwaySent();
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(remoteFlow, never()).incrementWindowSize(any(Http2Stream.class), anyInt());
        verify(listener, never()).onWindowUpdateRead(eq(ctx), anyInt(), anyInt());
    }

    @Test
    public void windowUpdateReadAfterGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint() throws Exception {
        mockGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint();
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(remoteFlow).incrementWindowSize(any(Http2Stream.class), anyInt());
        verify(listener).onWindowUpdateRead(eq(ctx), anyInt(), anyInt());
    }

    @Test(expected = Http2Exception.class)
    public void windowUpdateReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(false);
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
    }

    @Test
    public void windowUpdateReadForUnknownStreamShouldBeIgnored() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(remoteFlow, never()).incrementWindowSize(any(Http2Stream.class), anyInt());
        verify(listener, never()).onWindowUpdateRead(eq(ctx), anyInt(), anyInt());
    }

    @Test
    public void windowUpdateReadShouldSucceed() throws Exception {
        decode().onWindowUpdateRead(ctx, STREAM_ID, 10);
        verify(remoteFlow).incrementWindowSize(eq(stream), eq(10));
        verify(listener).onWindowUpdateRead(eq(ctx), eq(STREAM_ID), eq(10));
    }

    @Test
    public void rstStreamReadAfterGoAwayShouldSucceed() throws Exception {
        when(connection.goAwaySent()).thenReturn(true);
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).closeStream(eq(stream), eq(future));
        verify(listener).onRstStreamRead(eq(ctx), anyInt(), anyLong());
    }

    @Test(expected = Http2Exception.class)
    public void rstStreamReadForUnknownStreamShouldThrow() throws Exception {
        when(connection.streamMayHaveExisted(STREAM_ID)).thenReturn(false);
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
    }

    @Test
    public void rstStreamReadForUnknownStreamShouldBeIgnored() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager, never()).closeStream(eq(stream), eq(future));
        verify(listener, never()).onRstStreamRead(eq(ctx), anyInt(), anyLong());
    }

    @Test
    public void rstStreamReadShouldCloseStream() throws Exception {
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).closeStream(eq(stream), eq(future));
        verify(listener).onRstStreamRead(eq(ctx), eq(STREAM_ID), eq(PROTOCOL_ERROR.code()));
    }

    @Test(expected = Http2Exception.class)
    public void rstStreamOnIdleStreamShouldThrow() throws Exception {
        when(stream.state()).thenReturn(IDLE);
        decode().onRstStreamRead(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).closeStream(eq(stream), eq(future));
        verify(listener, never()).onRstStreamRead(any(ChannelHandlerContext.class), anyInt(), anyLong());
    }

    @Test
    public void pingReadWithAckShouldNotifyListener() throws Exception {
        decode().onPingAckRead(ctx, 0L);
        verify(listener).onPingAckRead(eq(ctx), eq(0L));
    }

    @Test
    public void pingReadShouldReplyWithAck() throws Exception {
        decode().onPingRead(ctx, 0L);
        verify(encoder).writePing(eq(ctx), eq(true), eq(0L), eq(promise));
        verify(listener, never()).onPingAckRead(eq(ctx), any(long.class));
    }

    @Test
    public void settingsReadWithAckShouldNotifyListener() throws Exception {
        decode().onSettingsAckRead(ctx);
        // Take into account the time this was called during setup().
        verify(listener, times(2)).onSettingsAckRead(eq(ctx));
    }

    @Test
    public void settingsReadShouldSetValues() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.pushEnabled(true);
        settings.initialWindowSize(123);
        settings.maxConcurrentStreams(456);
        settings.headerTableSize(789);
        decode().onSettingsRead(ctx, settings);
        verify(encoder).remoteSettings(settings);
        verify(listener).onSettingsRead(eq(ctx), eq(settings));
    }

    @Test
    public void goAwayShouldReadShouldUpdateConnectionState() throws Exception {
        decode().onGoAwayRead(ctx, 1, 2L, EMPTY_BUFFER);
        verify(connection).goAwayReceived(eq(1), eq(2L), eq(EMPTY_BUFFER));
        verify(listener).onGoAwayRead(eq(ctx), eq(1), eq(2L), eq(EMPTY_BUFFER));
    }

    private static ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return wrappedBuffer("abcdefgh".getBytes(UTF_8));
    }

    /**
     * Calls the decode method on the handler and gets back the captured internal listener
     */
    private Http2FrameListener decode() throws Exception {
        ArgumentCaptor<Http2FrameListener> internalListener = ArgumentCaptor.forClass(Http2FrameListener.class);
        doNothing().when(reader).readFrame(eq(ctx), any(ByteBuf.class), internalListener.capture());
        decoder.decodeFrame(ctx, EMPTY_BUFFER, Collections.emptyList());
        return internalListener.getValue();
    }

    private void mockFlowControl(final int processedBytes) throws Http2Exception {
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                return processedBytes;
            }
        }).when(listener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());
    }

    private void mockGoAwaySent() {
        when(connection.goAwaySent()).thenReturn(true);
        when(remote.isValidStreamId(STREAM_ID)).thenReturn(true);
        when(remote.lastStreamKnownByPeer()).thenReturn(0);
    }

    private void mockGoAwaySentShouldAllowFramesForStreamCreatedByLocalEndpoint() {
        when(connection.goAwaySent()).thenReturn(true);
        when(remote.isValidStreamId(STREAM_ID)).thenReturn(false);
        when(remote.lastStreamKnownByPeer()).thenReturn(0);
    }
}
