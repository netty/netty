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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.PooledByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelConfig;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelMetadata;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultChannelConfig;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http2.Http2RemoteFlowController.FlowControlled;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import junit.framework.AssertionFailedError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty5.buffer.Unpooled.wrappedBuffer;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty5.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty5.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty5.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty5.util.CharsetUtil.UTF_8;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultHttp2ConnectionEncoder}
 */
@SuppressWarnings("unchecked")
public class DefaultHttp2ConnectionEncoderTest {
    private static final int STREAM_ID = 2;
    private static final int PUSH_STREAM_ID = 4;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private Channel.Unsafe unsafe;

    @Mock
    private ChannelPipeline pipeline;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private Http2FrameWriter.Configuration writerConfig;

    @Mock
    private Http2FrameSizePolicy frameSizePolicy;

    @Mock
    private Http2LifecycleManager lifecycleManager;

    private DefaultHttp2ConnectionEncoder encoder;
    private Http2Connection connection;
    private ArgumentCaptor<Http2RemoteFlowController.FlowControlled> payloadCaptor;
    private List<String> writtenData;
    private List<Integer> writtenPadding;
    private boolean streamClosed;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        ChannelMetadata metadata = new ChannelMetadata(false, 16);
        when(channel.isActive()).thenReturn(true);
        when(channel.pipeline()).thenReturn(pipeline);
        when(channel.metadata()).thenReturn(metadata);
        when(channel.unsafe()).thenReturn(unsafe);
        ChannelConfig config = new DefaultChannelConfig(channel);
        when(channel.config()).thenReturn(config);

        when(writer.configuration()).thenReturn(writerConfig);
        when(writerConfig.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(64);
        doAnswer((Answer<Future<Void>>) in -> ImmediateEventExecutor.INSTANCE.newSucceededFuture(null))
                .when(writer).writeSettings(eq(ctx), any(Http2Settings.class));
        doAnswer((Answer<Future<Void>>) in -> {
            ((ByteBuf) in.getArguments()[3]).release();
            return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
        }).when(writer).writeGoAway(eq(ctx), anyInt(), anyInt(), any(ByteBuf.class));

        writtenData = new ArrayList<>();
        writtenPadding = new ArrayList<>();
        when(writer.writeData(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean()))
                .then((Answer<Future<Void>>) in -> {
                    // Make sure we only receive stream closure on the last frame and that void promises
                    // are used for all writes except the last one.
                    if (streamClosed) {
                        fail("Stream already closed");
                    } else {
                        streamClosed = (Boolean) in.getArguments()[4];
                    }
                    writtenPadding.add((Integer) in.getArguments()[3]);
                    ByteBuf data = (ByteBuf) in.getArguments()[2];
                    writtenData.add(data.toString(UTF_8));
                    // Release the buffer just as DefaultHttp2FrameWriter does
                    data.release();
                    // Let the promise succeed to trigger listeners.
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        when(writer.writeHeaders(eq(ctx), anyInt(), any(Http2Headers.class), anyInt(), anyShort(), anyBoolean(),
                anyInt(), anyBoolean()))
                .then((Answer<Future<Void>>) invocationOnMock -> {
                    if (streamClosed) {
                        fail("Stream already closed");
                    } else {
                        streamClosed = (Boolean) invocationOnMock.getArguments()[5];
                    }
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        when(writer.writeHeaders(eq(ctx), anyInt(), any(Http2Headers.class),
                anyInt(), anyBoolean()))
                .then((Answer<Future<Void>>) invocationOnMock -> {
                    if (streamClosed) {
                        fail("Stream already closed");
                    } else {
                        streamClosed = invocationOnMock.getArgument(4);
                    }
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        payloadCaptor = ArgumentCaptor.forClass(Http2RemoteFlowController.FlowControlled.class);
        doNothing().when(remoteFlow).addFlowControlled(any(Http2Stream.class), payloadCaptor.capture());
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        doAnswer((Answer<Promise<Void>>) in -> ImmediateEventExecutor.INSTANCE.newPromise()).when(ctx).newPromise();
        doAnswer((Answer<Future<Void>>) in -> ImmediateEventExecutor.INSTANCE.newSucceededFuture(null))
                .when(ctx).newSucceededFuture();
        doAnswer((Answer<Future<Void>>) in -> ImmediateEventExecutor.INSTANCE.newFailedFuture(in.getArgument(0)))
                .when(ctx).newFailedFuture(any(Throwable.class));
        when(ctx.flush()).thenThrow(new AssertionFailedError("forbidden"));
        when(channel.alloc()).thenReturn(PooledByteBufAllocator.DEFAULT);
        doAnswer((Answer<Future<Void>>) in -> ImmediateEventExecutor.INSTANCE.newFailedFuture(in.getArgument(0)))
                .when(channel).newFailedFuture(any(Throwable.class));
        // Use a server-side connection so we can test server push.
        connection = new DefaultHttp2Connection(true);
        connection.remote().flowController(remoteFlow);

        encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder.lifecycleManager(lifecycleManager);
    }

    @Test
    public void dataWithEndOfStreamWriteShouldSignalThatFrameWasConsumedOnError() throws Exception {
        dataWriteShouldSignalThatFrameWasConsumedOnError0(true);
    }

    @Test
    public void dataWriteShouldSignalThatFrameWasConsumedOnError() throws Exception {
        dataWriteShouldSignalThatFrameWasConsumedOnError0(false);
    }

    private void dataWriteShouldSignalThatFrameWasConsumedOnError0(boolean endOfStream) throws Exception {
        createStream(STREAM_ID, false);
        final ByteBuf data = dummyData();
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data, 0, endOfStream);

        FlowControlled controlled = payloadCaptor.getValue();
        assertEquals(8, controlled.size());
        payloadCaptor.getValue().write(ctx, 4);
        assertEquals(4, controlled.size());

        Throwable error = new IllegalStateException();
        payloadCaptor.getValue().error(ctx, error);
        payloadCaptor.getValue().write(ctx, 8);
        assertEquals(0, controlled.size());
        assertEquals("abcd", writtenData.get(0));
        assertEquals(0, data.refCnt());
        assertSame(error, f.cause());
    }

    @Test
    public void dataWriteShouldSucceed() throws Exception {
        createStream(STREAM_ID, false);
        final ByteBuf data = dummyData();
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data, 0, true);
        assertEquals(8, payloadCaptor.getValue().size());
        payloadCaptor.getValue().write(ctx, 8);
        assertEquals(0, payloadCaptor.getValue().size());
        assertEquals("abcdefgh", writtenData.get(0));
        assertEquals(0, data.refCnt());
        assertTrue(f.isSuccess());
    }

    @Test
    public void dataFramesShouldMerge() throws Exception {
        createStream(STREAM_ID, false);
        final ByteBuf data = dummyData().retain();

        Future<Void> future1 = encoder.writeData(ctx, STREAM_ID, data, 0, true);
        Future<Void> future2 = encoder.writeData(ctx, STREAM_ID, data, 0, true);

        // Now merge the two payloads.
        List<FlowControlled> capturedWrites = payloadCaptor.getAllValues();
        FlowControlled mergedPayload = capturedWrites.get(0);
        mergedPayload.merge(ctx, capturedWrites.get(1));
        assertEquals(16, mergedPayload.size());
        assertFalse(future1.isDone());
        assertFalse(future2.isDone());

        // Write the merged payloads and verify it was written correctly.
        mergedPayload.write(ctx, 16);
        assertEquals(0, mergedPayload.size());
        assertEquals("abcdefghabcdefgh", writtenData.get(0));
        assertEquals(0, data.refCnt());
        assertTrue(future1.isSuccess());
        assertTrue(future2.isSuccess());
    }

    @Test
    public void dataFramesDontMergeWithHeaders() throws Exception {
        createStream(STREAM_ID, false);
        final ByteBuf data = dummyData().retain();
        encoder.writeData(ctx, STREAM_ID, data, 0, false);
        when(remoteFlow.hasFlowControlled(any(Http2Stream.class))).thenReturn(true);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);
        List<FlowControlled> capturedWrites = payloadCaptor.getAllValues();
        assertFalse(capturedWrites.get(0).merge(ctx, capturedWrites.get(1)));
    }

    @Test
    public void emptyFrameShouldSplitPadding() throws Exception {
        ByteBuf data = Unpooled.buffer(0);
        assertSplitPaddingOnEmptyBuffer(data);
        assertEquals(0, data.refCnt());
    }

    private void assertSplitPaddingOnEmptyBuffer(ByteBuf data) throws Exception {
        createStream(STREAM_ID, false);
        when(frameSizePolicy.maxFrameSize()).thenReturn(5);
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data, 10, true);
        assertEquals(10, payloadCaptor.getValue().size());
        payloadCaptor.getValue().write(ctx, 10);
        // writer was called 2 times
        assertEquals(1, writtenData.size());
        assertEquals("", writtenData.get(0));
        assertEquals(10, (int) writtenPadding.get(0));
        assertEquals(0, data.refCnt());
        assertTrue(f.isSuccess());
    }

    @Test
    public void headersWriteForUnknownStreamShouldCreateStream() throws Exception {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        Future<Void> f = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(writer).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(0),
                eq(false));
        assertTrue(f.isSuccess());
    }

    @Test
    public void headersWriteShouldOpenStreamForPush() throws Exception {
        writeAllFlowControlledFrames();
        Http2Stream parent = createStream(STREAM_ID, false);
        reservePushStream(PUSH_STREAM_ID, parent);

        encoder.writeHeaders(ctx, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        assertEquals(HALF_CLOSED_REMOTE, stream(PUSH_STREAM_ID).state());
        verify(writer).writeHeaders(eq(ctx), eq(PUSH_STREAM_ID), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void trailersDoNotEndStreamThrows() {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());

        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void trailersDoNotEndStreamWithDataThrows() {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);

        Http2Stream stream = connection.stream(streamId);
        when(remoteFlow.hasFlowControlled(eq(stream))).thenReturn(true);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());

        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void tooManyHeadersNoEOSThrows() {
        tooManyHeadersThrows(false);
    }

    @Test
    public void tooManyHeadersEOSThrows() {
        tooManyHeadersThrows(true);
    }

    private void tooManyHeadersThrows(boolean eos) {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, true);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, eos);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());

        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(true));
    }

    @Test
    public void infoHeadersAndTrailersAllowed() throws Exception {
        infoHeadersAndTrailers(true, 1);
    }

    @Test
    public void multipleInfoHeadersAndTrailersAllowed() throws Exception {
        infoHeadersAndTrailers(true, 10);
    }

    @Test
    public void infoHeadersAndTrailersNoEOSThrows() throws Exception {
        infoHeadersAndTrailers(false, 1);
    }

    @Test
    public void multipleInfoHeadersAndTrailersNoEOSThrows() throws Exception {
        infoHeadersAndTrailers(false, 10);
    }

    private void infoHeadersAndTrailers(boolean eos, int infoHeaderCount) {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        Http2Headers infoHeaders = informationalHeaders();
        for (int i = 0; i < infoHeaderCount; ++i) {
            encoder.writeHeaders(ctx, streamId, infoHeaders, 0, false);
        }
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, eos);
        assertTrue(future.isDone());
        assertEquals(eos, future.isSuccess());

        verify(writer, times(infoHeaderCount)).writeHeaders(eq(ctx), eq(streamId), eq(infoHeaders),
                eq(0), eq(false));
        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
        if (eos) {
            verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                    eq(0), eq(true));
        }
    }

    private static Http2Headers informationalHeaders() {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.status(HttpResponseStatus.CONTINUE.codeAsText());
        return headers;
    }

    @Test
    public void tooManyHeadersWithDataNoEOSThrows() {
        tooManyHeadersWithDataThrows(false);
    }

    @Test
    public void tooManyHeadersWithDataEOSThrows() {
        tooManyHeadersWithDataThrows(true);
    }

    private void tooManyHeadersWithDataThrows(boolean eos) {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);

        Http2Stream stream = connection.stream(streamId);
        when(remoteFlow.hasFlowControlled(eq(stream))).thenReturn(true);

        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, true);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, eos);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());

        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(true));
    }

    @Test
    public void infoHeadersAndTrailersWithDataAllowed() {
        infoHeadersAndTrailersWithData(true, 1);
    }

    @Test
    public void multipleInfoHeadersAndTrailersWithDataAllowed() {
        infoHeadersAndTrailersWithData(true, 10);
    }

    @Test
    public void infoHeadersAndTrailersWithDataNoEOSThrows() {
        infoHeadersAndTrailersWithData(false, 1);
    }

    @Test
    public void multipleInfoHeadersAndTrailersWithDataNoEOSThrows() {
        infoHeadersAndTrailersWithData(false, 10);
    }

    private void infoHeadersAndTrailersWithData(boolean eos, int infoHeaderCount) {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        Http2Headers infoHeaders = informationalHeaders();
        for (int i = 0; i < infoHeaderCount; ++i) {
            encoder.writeHeaders(ctx, streamId, infoHeaders, 0, false);
        }

        Http2Stream stream = connection.stream(streamId);
        when(remoteFlow.hasFlowControlled(eq(stream))).thenReturn(true);

        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);

        Future<Void> future = encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, eos);
        assertTrue(future.isDone());
        assertEquals(eos, future.isSuccess());

        verify(writer, times(infoHeaderCount)).writeHeaders(eq(ctx), eq(streamId), eq(infoHeaders),
                eq(0), eq(false));
        verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
        if (eos) {
            verify(writer, times(1)).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                    eq(0), eq(true));
        }
    }

    @Test
    public void pushPromiseWriteAfterGoAwayReceivedShouldFail() throws Exception {
        createStream(STREAM_ID, false);
        goAwayReceived(0);
        Future<Void> future = encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
    }

    @Test
    public void pushPromiseWriteShouldReserveStream() throws Exception {
        createStream(STREAM_ID, false);
        encoder.writePushPromise(ctx, STREAM_ID, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0);
        assertEquals(RESERVED_LOCAL, stream(PUSH_STREAM_ID).state());
        verify(writer).writePushPromise(eq(ctx), eq(STREAM_ID), eq(PUSH_STREAM_ID),
                eq(EmptyHttp2Headers.INSTANCE), eq(0));
    }

    @Test
    public void priorityWriteAfterGoAwayShouldSucceed() throws Exception {
        createStream(STREAM_ID, false);
        goAwayReceived(Integer.MAX_VALUE);
        encoder.writePriority(ctx, STREAM_ID, 0, (short) 255, true);
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void priorityWriteShouldSetPriorityForStream() throws Exception {
        short weight = 255;
        encoder.writePriority(ctx, STREAM_ID, 0, weight, true);

        // Verify that this did NOT create a stream object.
        Http2Stream stream = stream(STREAM_ID);
        assertNull(stream);

        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq((short) 255), eq(true));
    }

    @Test
    public void priorityWriteOnPreviouslyExistingStreamShouldSucceed() throws Exception {
        createStream(STREAM_ID, false).close();
        short weight = 255;
        encoder.writePriority(ctx, STREAM_ID, 0, weight, true);
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(0), eq(weight), eq(true));
    }

    @Test
    public void priorityWriteOnPreviouslyExistingParentStreamShouldSucceed() throws Exception {
        final int parentStreamId = STREAM_ID + 2;
        createStream(STREAM_ID, false);
        createStream(parentStreamId, false).close();

        short weight = 255;
        encoder.writePriority(ctx, STREAM_ID, parentStreamId, weight, true);
        verify(writer).writePriority(eq(ctx), eq(STREAM_ID), eq(parentStreamId), eq(weight), eq(true));
    }

    @Test
    public void rstStreamWriteForUnknownStreamShouldIgnore() throws Exception {
        encoder.writeRstStream(ctx, 5, PROTOCOL_ERROR.code());
        verify(writer, never()).writeRstStream(eq(ctx), anyInt(), anyLong());
    }

    @Test
    public void rstStreamShouldCloseStream() throws Exception {
        // Create the stream and send headers.
        writeAllFlowControlledFrames();
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);

        // Now verify that a stream reset is performed.
        stream(STREAM_ID);
        encoder.writeRstStream(ctx, STREAM_ID, PROTOCOL_ERROR.code());
        verify(lifecycleManager).resetStream(eq(ctx), eq(STREAM_ID), anyLong());
    }

    @Test
    public void pingWriteAfterGoAwayShouldSucceed() throws Exception {
        goAwayReceived(0);
        encoder.writePing(ctx, false, 0L);
        verify(writer).writePing(eq(ctx), eq(false), eq(0L));
    }

    @Test
    public void pingWriteShouldSucceed() throws Exception {
        encoder.writePing(ctx, false, 0L);
        verify(writer).writePing(eq(ctx), eq(false), eq(0L));
    }

    @Test
    public void settingsWriteAfterGoAwayShouldSucceed() throws Exception {
        goAwayReceived(0);
        encoder.writeSettings(ctx, new Http2Settings());
        verify(writer).writeSettings(eq(ctx), any(Http2Settings.class));
    }

    @Test
    public void settingsWriteShouldNotUpdateSettings() throws Exception {
        Http2Settings settings = new Http2Settings();
        settings.initialWindowSize(100);
        settings.maxConcurrentStreams(1000);
        settings.headerTableSize(2000);

        encoder.writeSettings(ctx, settings);
        verify(writer).writeSettings(eq(ctx), eq(settings));
    }

    @Test
    public void dataWriteShouldCreateHalfClosedStream() throws Exception {
        writeAllFlowControlledFrames();

        Http2Stream stream = createStream(STREAM_ID, false);
        ByteBuf data = dummyData();
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data.retain(), 0, true);
        assertTrue(f.isSuccess());
        verify(remoteFlow).addFlowControlled(eq(stream), any(FlowControlled.class));
        verify(lifecycleManager).closeStreamLocal(eq(stream), eq(f));
        assertEquals(data.toString(UTF_8), writtenData.get(0));
        data.release();
    }

    @Test
    public void headersWriteShouldHalfCloseStream() throws Exception {
        writeAllFlowControlledFrames();
        createStream(STREAM_ID, false);
        Future<Void> f = encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);

        assertTrue(f.isSuccess());
        verify(lifecycleManager).closeStreamLocal(eq(stream(STREAM_ID)), eq(f));
    }

    @Test
    public void headersWriteShouldHalfClosePushStream() throws Exception {
        writeAllFlowControlledFrames();
        Http2Stream parent = createStream(STREAM_ID, false);
        Http2Stream stream = reservePushStream(PUSH_STREAM_ID, parent);
        Future<Void> f = encoder.writeHeaders(ctx, PUSH_STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);
        assertEquals(HALF_CLOSED_REMOTE, stream.state());
        assertTrue(f.isSuccess());
        verify(lifecycleManager).closeStreamLocal(eq(stream), eq(f));
    }

    @Test
    public void headersWriteShouldHalfCloseAfterOnErrorForPreCreatedStream() throws Exception {
        final Throwable ex = new RuntimeException();
        // Fake an encoding error, like HPACK's HeaderListSizeException
        when(writer.writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0), eq(true)))
            .thenReturn(ImmediateEventExecutor.INSTANCE.newFailedFuture(ex));

        writeAllFlowControlledFrames();
        Http2Stream stream = createStream(STREAM_ID, false);
        Future<Void> f = encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);

        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertFalse(stream.isHeadersSent());
        InOrder inOrder = inOrder(lifecycleManager);
        inOrder.verify(lifecycleManager).onError(eq(ctx), eq(true), eq(ex));
        inOrder.verify(lifecycleManager).closeStreamLocal(eq(stream(STREAM_ID)), eq(f));
    }

    @Test
    public void headersWriteShouldHalfCloseAfterOnErrorForImplicitlyCreatedStream() throws Exception {
        final Throwable ex = new RuntimeException();
        // Fake an encoding error, like HPACK's HeaderListSizeException
        when(writer.writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE), eq(0), eq(true)))
            .thenReturn(ImmediateEventExecutor.INSTANCE.newFailedFuture(ex));

        writeAllFlowControlledFrames();
        Future<Void> f = encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, true);

        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertFalse(stream(STREAM_ID).isHeadersSent());
        InOrder inOrder = inOrder(lifecycleManager);
        inOrder.verify(lifecycleManager).onError(eq(ctx), eq(true), eq(ex));
        inOrder.verify(lifecycleManager).closeStreamLocal(eq(stream(STREAM_ID)), eq(f));
    }

    @Test
    public void encoderDelegatesGoAwayToLifeCycleManager() {
        encoder.writeGoAway(ctx, STREAM_ID, Http2Error.INTERNAL_ERROR.code(), null);
        verify(lifecycleManager).goAway(eq(ctx), eq(STREAM_ID), eq(Http2Error.INTERNAL_ERROR.code()),
                eq((ByteBuf) null));
        verifyNoMoreInteractions(writer);
    }

    @Test
    public void dataWriteToClosedStreamShouldFail() throws Exception {
        createStream(STREAM_ID, false).close();
        ByteBuf data = mock(ByteBuf.class);
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data, 0, false);
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(IllegalArgumentException.class));
        verify(data).release();
    }

    @Test
    public void dataWriteToHalfClosedLocalStreamShouldFail() throws Exception {
        createStream(STREAM_ID, true);
        ByteBuf data = mock(ByteBuf.class);
        Future<Void> f = encoder.writeData(ctx, STREAM_ID, data, 0, false);
        assertTrue(f.isDone());
        assertFalse(f.isSuccess());
        assertThat(f.cause(), instanceOf(IllegalStateException.class));
        verify(data).release();
    }

    @Test
    public void canWriteDataFrameAfterGoAwaySent() throws Exception {
        Http2Stream stream = createStream(STREAM_ID, false);
        connection.goAwaySent(0, 0, EMPTY_BUFFER);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false);
        verify(remoteFlow).addFlowControlled(eq(stream), any(FlowControlled.class));
    }

    @Test
    public void canWriteHeaderFrameAfterGoAwaySent() throws Exception {
        writeAllFlowControlledFrames();
        createStream(STREAM_ID, false);
        goAwaySent(0);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(writer).writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void canWriteDataFrameAfterGoAwayReceived() throws Exception {
        Http2Stream stream = createStream(STREAM_ID, false);
        goAwayReceived(STREAM_ID);
        ByteBuf data = mock(ByteBuf.class);
        encoder.writeData(ctx, STREAM_ID, data, 0, false);
        verify(remoteFlow).addFlowControlled(eq(stream), any(FlowControlled.class));
    }

    @Test
    public void canWriteHeaderFrameAfterGoAwayReceived() throws Http2Exception {
        writeAllFlowControlledFrames();
        goAwayReceived(STREAM_ID);
        encoder.writeHeaders(ctx, STREAM_ID, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(writer).writeHeaders(eq(ctx), eq(STREAM_ID), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void headersWithNoPriority() {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 0, false);
        verify(writer).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE),
                eq(0), eq(false));
    }

    @Test
    public void headersWithPriority() {
        writeAllFlowControlledFrames();
        final int streamId = 6;
        encoder.writeHeaders(ctx, streamId, EmptyHttp2Headers.INSTANCE, 10, DEFAULT_PRIORITY_WEIGHT,
                true, 1, false);
        verify(writer).writeHeaders(eq(ctx), eq(streamId), eq(EmptyHttp2Headers.INSTANCE), eq(10),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(true), eq(1), eq(false));
    }

    private void writeAllFlowControlledFrames() {
        doAnswer((Answer<Void>) invocationOnMock -> {
            FlowControlled flowControlled = (FlowControlled) invocationOnMock.getArguments()[1];
            flowControlled.write(ctx, Integer.MAX_VALUE);
            flowControlled.writeComplete();
            return null;
        }).when(remoteFlow).addFlowControlled(any(Http2Stream.class), payloadCaptor.capture());
    }

    private Http2Stream createStream(int streamId, boolean halfClosed) throws Http2Exception {
        return connection.local().createStream(streamId, halfClosed);
    }

    private Http2Stream reservePushStream(int pushStreamId, Http2Stream parent) throws Http2Exception {
        return connection.local().reservePushStream(pushStreamId, parent);
    }

    private Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }

    private void goAwayReceived(int lastStreamId) throws Http2Exception {
        connection.goAwayReceived(lastStreamId, 0, EMPTY_BUFFER);
    }

    private void goAwaySent(int lastStreamId) throws Http2Exception {
        connection.goAwaySent(lastStreamId, 0, EMPTY_BUFFER);
    }

    private static ByteBuf dummyData() {
        // The buffer is purposely 8 bytes so it will even work for a ping frame.
        return wrappedBuffer("abcdefgh".getBytes(UTF_8));
    }
}
