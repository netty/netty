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

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.emptyPingBuf;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http2.Http2Exception.ClosedStreamCreationException;
import io.netty.handler.codec.http2.Http2RemoteFlowController.FlowControlled;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;

import java.util.ArrayList;
import java.util.List;

import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link DefaultHttp2ConnectionEncoder}
 */
public class DefaultHttp2ConnectionEncoderTest {
    private static final int STREAM_ID = 1;
    private static final int PUSH_STREAM_ID = 2;

    private Http2ConnectionEncoder encoder;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Connection.Endpoint<Http2RemoteFlowController> remote;

    @Mock
    private Http2Connection.Endpoint<Http2LocalFlowController> local;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private ChannelPromise promise;

    @Mock
    private ChannelPromise voidPromise;

    @Mock
    private ChannelFuture future;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2Stream pushStream;

    @Mock
    private Http2FrameListener listener;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private Http2FrameWriter.Configuration writerConfig;

    @Mock
    private Http2FrameSizePolicy frameSizePolicy;

    @Mock
    private Http2LifecycleManager lifecycleManager;

    private ArgumentCaptor<Http2RemoteFlowController.FlowControlled> payloadCaptor;
    private List<String> writtenData;
    private List<Integer> writtenPadding;
    private boolean streamClosed;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        promise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        when(voidPromise.addListener(Matchers.<GenericFutureListener<Future<? super Void>>>any())).thenThrow(
                new AssertionFailedError());
        when(voidPromise.addListeners(Matchers.<GenericFutureListener<Future<? super Void>>>any())).thenThrow(
                new AssertionFailedError());

        when(channel.isActive()).thenReturn(true);
        when(stream.id()).thenReturn(STREAM_ID);
        when(stream.state()).thenReturn(OPEN);
        when(stream.open(anyBoolean())).thenReturn(stream);
        when(pushStream.id()).thenReturn(PUSH_STREAM_ID);
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                Http2StreamVisitor visitor = in.getArgumentAt(0, Http2StreamVisitor.class);
                if (!visitor.visit(stream)) {
                    return stream;
                }
                return null;
            }
        }).when(connection).forEachActiveStream(any(Http2StreamVisitor.class));
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.local()).thenReturn(local);
        when(connection.remote()).thenReturn(remote);
        when(remote.flowController()).thenReturn(remoteFlow);
        when(writer.configuration()).thenReturn(writerConfig);
        when(writerConfig.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(64);
        when(local.createIdleStream(eq(STREAM_ID))).thenReturn(stream);
        when(local.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(remote.createIdleStream(eq(STREAM_ID))).thenReturn(stream);
        when(remote.reservePushStream(eq(PUSH_STREAM_ID), eq(stream))).thenReturn(pushStream);
        when(writer.writeSettings(eq(ctx), any(Http2Settings.class), eq(promise))).thenReturn(future);
        when(writer.writeGoAway(eq(ctx), anyInt(), anyInt(), any(ByteBuf.class), eq(promise)))
                .thenReturn(future);
        writtenData = new ArrayList<String>();
        writtenPadding = new ArrayList<Integer>();
        when(writer.writeData(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean(), any(ChannelPromise.class)))
                .then(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                // Make sure we only receive stream closure on the last frame and that void promises are used for
                // all writes except the last one.
                ChannelPromise receivedPromise = (ChannelPromise) invocationOnMock.getArguments()[5];
                if (streamClosed) {
                    fail("Stream already closed");
                } else {
                    streamClosed = (Boolean) invocationOnMock.getArguments()[4];
                }
                writtenPadding.add((Integer) invocationOnMock.getArguments()[3]);
                ByteBuf data = (ByteBuf) invocationOnMock.getArguments()[2];
                writtenData.add(data.toString(UTF_8));
                // Release the buffer just as DefaultHttp2FrameWriter does
                data.release();
                // Let the promise succeed to trigger listeners.
                receivedPromise.trySuccess();
                return future;
            }
        });
        when(writer.writeHeaders(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(),
                anyInt(), anyBoolean(), any(ChannelPromise.class)))
                .then(new Answer<Object>() {
                    @Override
                    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ChannelPromise receivedPromise = (ChannelPromise) invocationOnMock.getArguments()[8];
                        if (streamClosed) {
                            fail("Stream already closed");
                        } else {
                            streamClosed = (Boolean) invocationOnMock.getArguments()[5];
                        }
                        receivedPromise.trySuccess();
                        return future;
                    }
                });
        payloadCaptor = ArgumentCaptor.forClass(Http2RemoteFlowController.FlowControlled.class);
        doNothing().when(remoteFlow).addFlowControlled(eq(ctx), eq(stream), payloadCaptor.capture());
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.newSucceededFuture()).thenReturn(future);
        when(ctx.newPromise()).thenReturn(promise);
        when(ctx.write(any())).thenReturn(future);
        when(ctx.flush()).thenThrow(new AssertionFailedError("forbidden"));
        when(channel.alloc()).thenReturn(PooledByteBufAllocator.DEFAULT);

        encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder.lifecycleManager(lifecycleManager);
    }

    @Test
    public void dataWriteShouldSucceed() throws Exception {
        final ByteBuf data = dummyData();
        encoder.writeData(ctx, STREAM_ID, data, 0, true, promise);
        assertEquals(payloadCaptor.getValue().size(), 8);
        payloadCaptor.getValue().write(8);
        assertEquals(0, payloadCaptor.getValue().size());
        assertEquals("abcdefgh", writtenData.get(0));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void dataFramesShouldMerge() throws Exception {
        final ByteBuf data = dummyData().retain();
        DefaultChannelPromise secondPromise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        encoder.writeData(ctx, STREAM_ID, data, 0, true, promise);
        encoder.writeData(ctx, STREAM_ID, data, 0, true, secondPromise);
        List<FlowControlled> capturedWrites = payloadCaptor.getAllValues();
        FlowControlled mergedPayload = capturedWrites.get(0);
        mergedPayload.merge(capturedWrites.get(1));

        assertEquals(16, mergedPayload.size());
        assertFalse(secondPromise.isSuccess());
        mergedPayload.write(16);
        assertEquals(0, mergedPayload.size());
        assertEquals("abcdefghabcdefgh", writtenData.get(0));
        assertEquals(0, data.refCnt());
        // Second promise is notified after write of the merged payload completes
        assertTrue(secondPromise.isSuccess());
    }

    @Test
    public void dataFramesShouldMergeUseVoidPromise() throws Exception {
        final ByteBuf data = dummyData().retain();
        when(voidPromise.isVoid()).thenReturn(true);
        encoder.writeData(ctx, STREAM_ID, data, 0, true, voidPromise);
        encoder.writeData(ctx, STREAM_ID, data, 0, true, voidPromise);
        List<FlowControlled> capturedWrites = payloadCaptor.getAllValues();
        FlowControlled mergedPayload = capturedWrites.get(0);
        assertTrue(mergedPayload.merge(capturedWrites.get(1)));

        assertEquals(16, mergedPayload.size());
        mergedPayload.write(16);
        assertEquals(0, mergedPayload.size());
        assertEquals("abcdefghabcdefgh", writtenData.get(0));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void dataFramesDontMergeWithHeaders() throws Exception {
        final ByteBuf data = dummyData().retain();
        encoder.writeData(ctx, STREAM_ID, data, 0, true, promise);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        List<FlowControlled> capturedWrites = payloadCaptor.getAllValues();
        assertFalse(capturedWrites.get(0).merge(capturedWrites.get(1)));
    }

    @Test
    public void dataLargerThanMaxFrameSizeShouldBeSplit() throws Exception {
        when(frameSizePolicy.maxFrameSize()).thenReturn(3);
        final ByteBuf data = dummyData();
        encoder.writeData(ctx, STREAM_ID, data, 0, true, promise);
        assertEquals(payloadCaptor.getValue().size(), 8);
        payloadCaptor.getValue().write(8);
        // writer was called 3 times
        assertEquals(3, writtenData.size());
        assertEquals("abc", writtenData.get(0));
        assertEquals("def", writtenData.get(1));
        assertEquals("gh", writtenData.get(2));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void paddingSplitOverFrame() throws Exception {
        when(frameSizePolicy.maxFrameSize()).thenReturn(5);
        final ByteBuf data = dummyData();
        encoder.writeData(ctx, STREAM_ID, data, 5, true, promise);
        assertEquals(payloadCaptor.getValue().size(), 13);
        payloadCaptor.getValue().write(13);
        // writer was called 3 times
        assertEquals(3, writtenData.size());
        assertEquals("abcde", writtenData.get(0));
        assertEquals(0, (int) writtenPadding.get(0));
        assertEquals("fgh", writtenData.get(1));
        assertEquals(2, (int) writtenPadding.get(1));
        assertEquals("", writtenData.get(2));
        assertEquals(3, (int) writtenPadding.get(2));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void frameShouldSplitPadding() throws Exception {
        when(frameSizePolicy.maxFrameSize()).thenReturn(5);
        ByteBuf data = dummyData();
        encoder.writeData(ctx, STREAM_ID, data, 10, true, promise);
        assertEquals(payloadCaptor.getValue().size(), 18);
        payloadCaptor.getValue().write(18);
        // writer was called 4 times
        assertEquals(4, writtenData.size());
        assertEquals("abcde", writtenData.get(0));
        assertEquals(0, (int) writtenPadding.get(0));
        assertEquals("fgh", writtenData.get(1));
        assertEquals(2, (int) writtenPadding.get(1));
        assertEquals("", writtenData.get(2));
        assertEquals(5, (int) writtenPadding.get(2));
        assertEquals("", writtenData.get(3));
        assertEquals(3, (int) writtenPadding.get(3));
        assertEquals(0, data.refCnt());
    }

    @Test
    public void emptyFrameShouldSplitPadding() throws Exception {
        ByteBuf data = Unpooled.buffer(0);
        assertSplitPaddingOnEmptyBuffer(data);
        assertEquals(0, data.refCnt());
    }

    @Test
    public void singletonEmptyBufferShouldSplitPadding() throws Exception {
        assertSplitPaddingOnEmptyBuffer(Unpooled.EMPTY_BUFFER);
    }

    private void assertSplitPaddingOnEmptyBuffer(ByteBuf data) throws Exception {
        when(frameSizePolicy.maxFrameSize()).thenReturn(5);
        encoder.writeData(ctx, STREAM_ID, data, 10, true, promise);
        assertEquals(payloadCaptor.getValue().size(), 10);
        payloadCaptor.getValue().write(10);
        // writer was called 2 times
        assertEquals(2, writtenData.size());
        assertEquals("", writtenData.get(0));
        assertEquals(5, (int) writtenPadding.get(0));
        assertEquals("", writtenData.get(1));
        assertEquals(5, (int) writtenPadding.get(1));
    }

    @Test
    public void headersWriteForUnknownStreamShouldCreateStream() throws Exception {
        int streamId = 5;
        when(stream.id()).thenReturn(streamId);
        when(stream.state()).thenReturn(IDLE);
        mockFutureAddListener(true);
        when(local.createStream(eq(streamId), anyBoolean())).thenReturn(stream);
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(local).createStream(eq(streamId), eq(false));
        assertNotNull(payloadCaptor.getValue());
        payloadCaptor.getValue().write(0);
        verify(writer).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(promise));
    }

    @Test
    public void headersWriteShouldOpenStreamForPush() throws Exception {
        mockFutureAddListener(true);
        when(stream.state()).thenReturn(RESERVED_LOCAL);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(stream).open(false);
        verify(stream, never()).closeLocalSide();
        assertNotNull(payloadCaptor.getValue());
        payloadCaptor.getValue().write(0);
        verify(writer).writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                                    eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(false), eq(promise));
    }

    @Test
    public void pushPromiseWriteAfterGoAwayReceivedShouldFail() throws Exception {
        when(connection.goAwayReceived()).thenReturn(true);
        ChannelFuture future =  encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0,
                                                         promise);
        assertTrue(future.awaitUninterruptibly().cause() instanceof Http2Exception);
    }

    @Test
    public void pushPromiseWriteShouldReserveStream() throws Exception {
        encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, promise);
        verify(local).reservePushStream(eq(PUSH_STREAM_ID), eq(stream));
        verify(writer).writePushPromise(eq(ctx), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EmptyHttp2Headers.INSTANCE), eq(0), eq(promise));
    }

    @Test
    public void priorityWriteAfterGoAwayShouldSucceed() throws Exception {
        when(connection.goAwayReceived()).thenReturn(true);
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true), eq(promise));
    }

    @Test
    public void priorityWriteShouldSetPriorityForStream() throws Exception {
        when(connection.stream(STREAM_ID)).thenReturn(null);
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        verify(stream).setPriority(eq(0), eq((short) 255), eq(true));
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true), eq(promise));
        verify(local).createIdleStream(STREAM_ID);
        verify(stream, never()).open(anyBoolean());
    }

    @Test
    public void priorityWriteOnPreviouslyExistingStreamShouldSucceed() throws Exception {
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                throw new ClosedStreamCreationException(Http2Error.INTERNAL_ERROR);
            }
        }).when(local).createIdleStream(eq(STREAM_ID));
        when(connection.stream(STREAM_ID)).thenReturn(null);
        // Just return the stream object as the connection stream to ensure the dependent stream "exists"
        when(connection.stream(0)).thenReturn(stream);
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        verify(stream, never()).setPriority(anyInt(), anyShort(), anyBoolean());
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true), eq(promise));
        verify(local).createIdleStream(STREAM_ID);
    }

    @Test
    public void priorityWriteOnPreviouslyExistingParentStreamShouldSucceed() throws Exception {
        doAnswer(new Answer<Http2Stream>() {
            @Override
            public Http2Stream answer(InvocationOnMock in) throws Throwable {
                throw new ClosedStreamCreationException(Http2Error.INTERNAL_ERROR);
            }
        }).when(stream).setPriority(eq(0), eq((short) 255), eq(true));
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true, promise);
        verify(stream).setPriority(eq(0), eq((short) 255), eq(true));
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true), eq(promise));
    }

    @Test
    public void rstStreamWriteForUnknownStreamShouldIgnore() throws Exception {
        encoder.writeRstStream(ctx, 5, PROTOCOL_ERROR.code(), promise);
        verify(writer, never()).writeRstStream(eq(ctx), anyInt(), anyLong(), eq(promise));
    }

    @Test
    public void rstStreamWriteShouldCloseStream() throws Exception {
        encoder.writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code(), promise);
        verify(lifecycleManager).resetStream(eq(ctx), eq(STREAM_ID), eq(PROTOCOL_ERROR.code()), eq(promise));
    }

    @Test
    public void pingWriteAfterGoAwayShouldSucceed() throws Exception {
        when(connection.goAwayReceived()).thenReturn(true);
        encoder.writePing(ctx, false, emptyPingBuf(), promise);
        verify(writer).writePing(eq(ctx), eq(false), eq(emptyPingBuf()), eq(promise));
    }

    @Test
    public void pingWriteShouldSucceed() throws Exception {
        encoder.writePing(ctx, false, emptyPingBuf(), promise);
        verify(writer).writePing(eq(ctx), eq(false), eq(emptyPingBuf()), eq(promise));
    }

    @Test
    public void settingsWriteAfterGoAwayShouldSucceed() throws Exception {
        when(connection.goAwayReceived()).thenReturn(true);
        encoder.writeSettings(ctx, new Http2Settings(), promise);
        verify(writer).writeSettings(eq(ctx), any(Http2Settings.class), eq(promise));
    }

    @Test
    public void settingsWriteShouldNotUpdateSettings() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(100);
        settings.pushEnabled(false);
        settings.maxConcurrentStreams(1000);
        settings.headerTableSize(2000);
        encoder.writeSettings(ctx, settings, promise);
        verify(writer).writeSettings(eq(ctx), eq(settings), eq(promise));
    }

    @Test
    public void dataWriteShouldCreateHalfClosedStream() {
        mockSendFlowControlledWriteEverything();
        ByteBuf data = dummyData();
        encoder.writeData(ctx, STREAM_ID, data.retain(), 0, true, promise);
        verify(remoteFlow).addFlowControlled(eq(ctx), eq(stream), any(FlowControlled.class));
        verify(lifecycleManager).closeStreamLocal(stream, promise);
        assertEquals(data.toString(UTF_8), writtenData.get(0));
        data.release();
    }

    @Test
    public void headersWriteShouldHalfCloseStream() throws Exception {
        mockSendFlowControlledWriteEverything();
        int streamId = 5;
        when(stream.id()).thenReturn(streamId);
        when(stream.state()).thenReturn(HALF_CLOSED_LOCAL);
        mockFutureAddListener(true);
        when(local.createStream(eq(streamId), anyBoolean())).thenReturn(stream);
        when(writer.writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                                 eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(promise)))
                .thenReturn(future);
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, true, promise);
        verify(local).createStream(eq(streamId), eq(true));
        // Trigger the write and mark the promise successful to trigger listeners
        assertNotNull(payloadCaptor.getValue());
        payloadCaptor.getValue().write(0);
        promise.trySuccess();
        verify(lifecycleManager).closeStreamLocal(eq(stream), eq(promise));
    }

    @Test
    public void headersWriteShouldHalfClosePushStream() throws Exception {
        mockSendFlowControlledWriteEverything();
        mockFutureAddListener(true);
        when(stream.state()).thenReturn(RESERVED_LOCAL).thenReturn(HALF_CLOSED_LOCAL);
        when(writer.writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                                 eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true), eq(promise)))
                .thenReturn(future);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true, promise);
        verify(stream).open(true);

        promise.trySuccess();
        verify(lifecycleManager).closeStreamLocal(eq(stream), eq(promise));
    }

    @Test
    public void encoderDelegatesGoAwayToLifeCycleManager() {
        encoder.writeGoAway(ctx, STREAM_ID, Http2Error.INTERNAL_ERROR.code(), null, promise);
        verify(lifecycleManager).goAway(eq(ctx), eq(STREAM_ID), eq(Http2Error.INTERNAL_ERROR.code()),
                                        eq((ByteBuf) null), eq(promise));
        verifyNoMoreInteractions(writer);
    }

    @Test
    public void dataWriteToClosedStreamShouldFail() {
        when(stream.state()).thenReturn(CLOSED);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertThat(promise.cause(), instanceOf(IllegalStateException.class));
        verify(data).release();
    }

    @Test
    public void dataWriteToHalfClosedLocalStreamShouldFail() {
        when(stream.state()).thenReturn(HALF_CLOSED_LOCAL);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertThat(promise.cause(), instanceOf(IllegalStateException.class));
        verify(data).release();
    }

    @Test
    public void canWriteDataFrameAfterGoAwaySent() {
        when(connection.goAwaySent()).thenReturn(true);
        when(remote.lastStreamKnownByPeer()).thenReturn(0);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
        verify(remoteFlow).addFlowControlled(eq(ctx), eq(stream), any(FlowControlled.class));
    }

    @Test
    public void canWriteHeaderFrameAfterGoAwaySent() {
        when(connection.goAwaySent()).thenReturn(true);
        when(remote.lastStreamKnownByPeer()).thenReturn(0);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(remoteFlow).addFlowControlled(eq(ctx), eq(stream), any(FlowControlled.class));
    }

    @Test
    public void canWriteDataFrameAfterGoAwayReceived() {
        when(connection.goAwayReceived()).thenReturn(true);
        when(local.lastStreamKnownByPeer()).thenReturn(STREAM_ID);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false, promise);
        verify(remoteFlow).addFlowControlled(eq(ctx), eq(stream), any(FlowControlled.class));
    }

    @Test
    public void canWriteHeaderFrameAfterGoAwayReceived() {
        when(connection.goAwayReceived()).thenReturn(true);
        when(local.lastStreamKnownByPeer()).thenReturn(STREAM_ID);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false, promise);
        verify(remoteFlow).addFlowControlled(eq(ctx), eq(stream), any(FlowControlled.class));
    }

    private void mockSendFlowControlledWriteEverything() {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                FlowControlled flowControlled = (FlowControlled) invocationOnMock.getArguments()[2];
                flowControlled.write(Integer.MAX_VALUE);
                flowControlled.writeComplete();
                return null;
            }
        }).when(remoteFlow).addFlowControlled(eq(ctx), eq(stream), payloadCaptor.capture());
    }

    private void mockFutureAddListener(boolean success) {
        when(future.isSuccess()).thenReturn(success);
        if (!success) {
            when(future.cause()).thenReturn(new Exception("Fake Exception"));
        }
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ChannelFutureListener listener = (ChannelFutureListener) invocation.getArguments()[0];
                listener.operationComplete(future);
                return null;
            }
        }).when(future).addListener(any(ChannelFutureListener.class));
    }

    private static ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return wrappedBuffer("abcdefgh".getBytes(UTF_8));
    }
}
