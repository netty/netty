/*
 * Copyright 2015 The Netty Project
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

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.DefaultMessageSizeEstimator;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.handler.codec.http2.StreamBufferingEncoder.Http2GoAwayException;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.netty5.handler.codec.http2.Http2Error.CANCEL;
import static io.netty5.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty5.handler.codec.http2.Http2TestUtil.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link StreamBufferingEncoder}.
 */
@SuppressWarnings("unchecked")
public class StreamBufferingEncoderTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamBufferingEncoderTest.class);
    @AutoClose
    private StreamBufferingEncoder encoder;

    private Http2Connection connection;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private EventExecutor executor;

    /**
     * Init fields and do mocking.
     */
    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        Http2FrameWriter.Configuration configuration = mock(Http2FrameWriter.Configuration.class);
        Http2FrameSizePolicy frameSizePolicy = mock(Http2FrameSizePolicy.class);
        when(writer.configuration()).thenReturn(configuration);
        when(configuration.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(DEFAULT_MAX_FRAME_SIZE);
        when(writer.writeData(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean()))
                .thenAnswer(successAnswer());
        when(writer.writeRstStream(eq(ctx), anyInt(), anyLong())).thenAnswer(successAnswer());
        when(writer.writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(Buffer.class)))
                .thenAnswer(successAnswer());
        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
            anyInt(), anyBoolean())).thenAnswer(noopAnswer());
        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
            anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean()))
            .thenAnswer(noopAnswer());

        connection = new DefaultHttp2Connection(false);
        connection.remote().flowController(new DefaultHttp2RemoteFlowController(connection));
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection).frameWriter(writer));

        DefaultHttp2ConnectionEncoder defaultEncoder =
                new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder = new StreamBufferingEncoder(defaultEncoder);
        DefaultHttp2ConnectionDecoder decoder =
                new DefaultHttp2ConnectionDecoder(connection, encoder, mock(Http2FrameReader.class));
        Http2ConnectionHandler handler = new Http2ConnectionHandlerBuilder()
                .frameListener(mock(Http2FrameListener.class))
                .codec(decoder, encoder).build();

        // Set LifeCycleManager on encoder and decoder
        when(ctx.channel()).thenReturn(channel);
        when(ctx.bufferAllocator()).thenReturn(onHeapAllocator());
        when(channel.bufferAllocator()).thenReturn(onHeapAllocator());
        when(executor.inEventLoop()).thenReturn(true);
        doAnswer((Answer<Promise<Void>>) invocation -> newPromise()).when(ctx).newPromise();
        doAnswer((Answer<Future<Void>>) invocation -> ImmediateEventExecutor.INSTANCE.newSucceededFuture(null))
                .when(ctx).newSucceededFuture();

        doAnswer((Answer<Future<Void>>) invocation ->
                ImmediateEventExecutor.INSTANCE.newFailedFuture(invocation.getArgument(0)))
                .when(ctx).newFailedFuture(any(Throwable.class));

        when(ctx.executor()).thenReturn(executor);
        when(ctx.close()).thenReturn(newPromise().asFuture());
        when(channel.isActive()).thenReturn(false);
        when(channel.isWritable()).thenReturn(true);
        when(channel.writableBytes()).thenReturn(Long.MAX_VALUE);
        when(channel.getOption(ChannelOption.WRITE_BUFFER_WATER_MARK)).thenReturn(
                new WriteBufferWaterMark(1024, Integer.MAX_VALUE));
        when(channel.getOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR)).thenReturn(DefaultMessageSizeEstimator.DEFAULT);
        handler.handlerAdded(ctx);
    }

    @Test
    public void multipleWritesToActiveStream() {
        encoder.writeSettingsAck(ctx);
        encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());
        Buffer data = data();
        final int expectedBytes = data.readableBytes() * 3;
        encoder.writeData(ctx, 3, data, 0, false);
        encoder.writeData(ctx, 3, data(), 0, false);
        encoder.writeData(ctx, 3, data(), 0, false);
        when(writer.writeData(any(ChannelHandlerContext.class), eq(3), any(Buffer.class), eq(0), eq(false)))
                .thenAnswer(inv -> {
                    try (Buffer buffer = inv.getArgument(2)) {
                        assertEquals(expectedBytes, buffer.readableBytes());
                    }
                    return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
                });
        encoderWriteHeaders(3);

        writeVerifyWriteHeaders(times(1), 3);
        // Contiguous data writes are coalesced
        ArgumentCaptor<Buffer> bufCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(writer, times(1)).writeData(any(ChannelHandlerContext.class), eq(3),
                bufCaptor.capture(), eq(0), eq(false));
    }

    @Test
    public void ensureCanCreateNextStreamWhenStreamCloses() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());

        // This one gets buffered.
        encoderWriteHeaders(5);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        // Now prevent us from creating another stream.
        setMaxConcurrentStreams(0);

        // Close the previous stream.
        connection.stream(3).close();

        // Ensure that no streams are currently active and that only the HEADERS from the first
        // stream were written.
        writeVerifyWriteHeaders(times(1), 3);
        writeVerifyWriteHeaders(never(), 5);
        assertEquals(0, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());
    }

    @Test
    public void alternatingWritesToActiveAndBufferedStreams() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());

        encoderWriteHeaders(5);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        Buffer empty = empty(); // Will be closed with the encoder.
        encoder.writeData(ctx, 3, empty, 0, false);
        writeVerifyWriteHeaders(times(1), 3);
        // data is cached in pendingStream.frames
        encoder.writeData(ctx, 5, empty, 0, false);
        verify(writer, never())
                .writeData(eq(ctx), eq(5), any(Buffer.class), eq(0), eq(false));
    }

    @Test
    public void bufferingNewStreamFailsAfterGoAwayReceived() throws Http2Exception {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(0);
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            connection.goAwayReceived(1, 8, empty);
        }

        Future<Void> future = encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
    }

    @Test
    public void receivingGoAwayFailsBufferedStreams() throws Http2Exception {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(5);

        int streamId = 3;
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            futures.add(encoderWriteHeaders(streamId));
            streamId += 2;
        }
        assertEquals(5, connection.numActiveStreams());
        assertEquals(4, encoder.numBufferedStreams());

        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            connection.goAwayReceived(11, 8, empty);
        }

        assertEquals(5, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
        int failCount = 0;
        for (Future<Void> f : futures) {
            if (f.isFailed()) {
                assertTrue(f.cause() instanceof Http2GoAwayException);
                failCount++;
            }
        }
        assertEquals(4, failCount);
    }

    @Test
    public void receivingGoAwayFailsNewStreamIfMaxConcurrentStreamsReached() throws Exception {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(1);
        encoderWriteHeaders(3);
        try (Buffer empty = empty()) {
            // the buffer ownership belongs to the caller
            connection.goAwayReceived(11, 8, empty);
        }
        Future<Void> f = encoderWriteHeaders(5);

        assertThat(f.asStage().getCause()).isInstanceOf(Http2GoAwayException.class);
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void sendingGoAwayShouldNotFailStreams() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(1);

        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
              anyBoolean())).thenAnswer(successAnswer());
        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
              anyShort(), anyBoolean(), anyInt(), anyBoolean())).thenAnswer(successAnswer());

        Future<Void> f1 = encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());
        Future<Void> f2 = encoderWriteHeaders(5);
        assertEquals(1, encoder.numBufferedStreams());
        Future<Void> f3 = encoderWriteHeaders(7);
        assertEquals(2, encoder.numBufferedStreams());

        encoder.writeGoAway(ctx, 3, CANCEL.code(), empty());

        assertEquals(1, connection.numActiveStreams());
        assertEquals(2, encoder.numBufferedStreams());
        // As the first stream did exists the first future should be done.
        assertTrue(f1.isDone());
        assertFalse(f2.isDone());
        assertFalse(f3.isDone());
    }

    @Test
    public void endStreamDoesNotFailBufferedStream() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3);
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeData(ctx, 3, empty(), 0, true);

        assertEquals(0, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 1.
        setMaxConcurrentStreams(1);
        encoder.writeSettingsAck(ctx);

        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
        assertEquals(HALF_CLOSED_LOCAL, connection.stream(3).state());
    }

    @Test
    public void rstStreamClosesBufferedStream() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3);
        assertEquals(1, encoder.numBufferedStreams());

        Future<Void> rstStreamFuture = encoder.writeRstStream(ctx, 3, CANCEL.code());
        assertTrue(rstStreamFuture.isSuccess());
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilActiveStreamsAreReset() throws Exception {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3);
        assertEquals(0, encoder.numBufferedStreams());
        encoderWriteHeaders(5);
        assertEquals(1, encoder.numBufferedStreams());
        encoderWriteHeaders(7);
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3);
        writeVerifyWriteHeaders(never(), 5);
        writeVerifyWriteHeaders(never(), 7);

        encoder.writeRstStream(ctx, 3, CANCEL.code());
        connection.remote().flowController().writePendingBytes();
        writeVerifyWriteHeaders(times(1), 5);
        writeVerifyWriteHeaders(never(), 7);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeRstStream(ctx, 5, CANCEL.code());
        connection.remote().flowController().writePendingBytes();
        writeVerifyWriteHeaders(times(1), 7);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());

        encoder.writeRstStream(ctx, 7, CANCEL.code());
        assertEquals(0, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilMaxStreamsIncreased() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(2);

        encoderWriteHeaders(3);
        encoderWriteHeaders(5);
        encoderWriteHeaders(7);
        encoderWriteHeaders(9);
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3);
        writeVerifyWriteHeaders(times(1), 5);
        writeVerifyWriteHeaders(never(), 7);
        writeVerifyWriteHeaders(never(), 9);

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 5.
        setMaxConcurrentStreams(5);
        encoder.writeSettingsAck(ctx);

        assertEquals(0, encoder.numBufferedStreams());
        writeVerifyWriteHeaders(times(1), 7);
        writeVerifyWriteHeaders(times(1), 9);

        encoderWriteHeaders(11);

        writeVerifyWriteHeaders(times(1), 11);

        assertEquals(5, connection.local().numActiveStreams());
    }

    @Test
    public void bufferUntilSettingsReceived() throws Http2Exception {
        int initialLimit = SMALLEST_MAX_CONCURRENT_STREAMS;
        int numStreams = initialLimit * 2;
        for (int ix = 0, nextStreamId = 3; ix < numStreams; ++ix, nextStreamId += 2) {
            encoderWriteHeaders(nextStreamId);
            if (ix < initialLimit) {
                writeVerifyWriteHeaders(times(1), nextStreamId);
            } else {
                writeVerifyWriteHeaders(never(), nextStreamId);
            }
        }
        assertEquals(numStreams / 2, encoder.numBufferedStreams());

        // Simulate that we received a SETTINGS frame.
        setMaxConcurrentStreams(initialLimit * 2);

        assertEquals(0, encoder.numBufferedStreams());
        assertEquals(numStreams, connection.local().numActiveStreams());
    }

    @Test
    public void bufferUntilSettingsReceivedWithNoMaxConcurrentStreamValue() throws Http2Exception {
        int initialLimit = SMALLEST_MAX_CONCURRENT_STREAMS;
        int numStreams = initialLimit * 2;
        for (int ix = 0, nextStreamId = 3; ix < numStreams; ++ix, nextStreamId += 2) {
            encoderWriteHeaders(nextStreamId);
            if (ix < initialLimit) {
                writeVerifyWriteHeaders(times(1), nextStreamId);
            } else {
                writeVerifyWriteHeaders(never(), nextStreamId);
            }
        }
        assertEquals(numStreams / 2, encoder.numBufferedStreams());

        // Simulate that we received an empty SETTINGS frame.
        encoder.remoteSettings(new Http2Settings());

        assertEquals(0, encoder.numBufferedStreams());
        assertEquals(numStreams, connection.local().numActiveStreams());
    }

    @Test
    public void exhaustedStreamsDoNotBuffer() throws Exception {
        // Write the highest possible stream ID for the client.
        // This will cause the next stream ID to be negative.
        encoderWriteHeaders(Integer.MAX_VALUE);

        // Disallow any further streams.
        setMaxConcurrentStreams(0);

        // Simulate numeric overflow for the next stream ID.
        Future<Void> f = encoderWriteHeaders(-1);

        // Verify that the write fails.
        assertNotNull(f.asStage().getCause());
    }

    @Test
    public void closedBufferedStreamReleasesBuffer() {
        encoder.writeSettingsAck(ctx);
        setMaxConcurrentStreams(0);
        Buffer data = mock(Buffer.class);
        Future<Void> f1 = encoderWriteHeaders(3);
        assertEquals(1, encoder.numBufferedStreams());
        Future<Void> f2 = encoder.writeData(ctx, 3, data, 0, false);

        Future<Void> rstFuture = encoder.writeRstStream(ctx, 3, CANCEL.code());

        assertEquals(0, encoder.numBufferedStreams());
        assertTrue(rstFuture.isSuccess());
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());
        verify(data).close();
    }

    @Test
    public void closeShouldCancelAllBufferedStreams() throws Exception {
        encoder.writeSettingsAck(ctx);
        connection.local().maxActiveStreams(0);

        Future<Void> f1 = encoderWriteHeaders(3);
        Future<Void> f2 = encoderWriteHeaders(5);
        Future<Void> f3 = encoderWriteHeaders(7);

        encoder.close();
        assertNotNull(f1.asStage().getCause());
        assertNotNull(f2.asStage().getCause());
        assertNotNull(f3.asStage().getCause());
    }

    @Test
    public void headersAfterCloseShouldImmediatelyFail() {
        encoder.writeSettingsAck(ctx);
        encoder.close();

        Future<Void> f = encoderWriteHeaders(3);
        assertNotNull(f.cause());
    }

    @Test
    public void testExhaustedStreamId() throws Http2Exception {
        testStreamId(Integer.MAX_VALUE - 2);
        testStreamId(connection.local().incrementAndGetNextStreamId());
    }

    private void testStreamId(int nextStreamId) throws Http2Exception {
        connection.local().createStream(nextStreamId, false);
        try (Buffer empty = empty()) {
            Future<Void> channelFuture = encoder.writeData(ctx, nextStreamId, empty, 0, false);
            assertFalse(channelFuture.isFailed());
        }
    }

    private void setMaxConcurrentStreams(int newValue) {
        try {
            encoder.remoteSettings(new Http2Settings().maxConcurrentStreams(newValue));
            // Flush the remote flow controller to write data
            encoder.flowController().writePendingBytes();
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Future<Void> encoderWriteHeaders(int streamId) {
        Future<Void> future =
                encoder.writeHeaders(ctx, streamId, Http2Headers.newHeaders(), 0, DEFAULT_PRIORITY_WEIGHT,
                             false, 0, false);
        try {
            encoder.flowController().writePendingBytes();
            return future;
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeVerifyWriteHeaders(VerificationMode mode, int streamId) {
        verify(writer, mode).writeHeaders(eq(ctx), eq(streamId), any(Http2Headers.class), eq(0),
                                          eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0),
                                          eq(false));
    }

    private static Answer<Future<Void>> successAnswer() {
        return invocation -> {
            for (Object a : invocation.getArguments()) {
                SilentDispose.dispose(a, logger);
            }

            return ImmediateEventExecutor.INSTANCE.newSucceededFuture(null);
        };
    }

    private static Answer<Future<Void>> noopAnswer() {
        return new Answer<Future<Void>>() {
            @Override
            public Future<Void> answer(InvocationOnMock invocation) throws Throwable {
                for (Object a : invocation.getArguments()) {
                    if (a instanceof Promise) {
                        return ((Promise<Void>) a).asFuture();
                    }
                }
                return newPromise().asFuture();
            }
        };
    }

    private static Promise<Void> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }

    private static Buffer data() {
        Buffer buf = onHeapAllocator().allocate(10);
        for (int i = 0; i < buf.writableBytes(); i++) {
            buf.writeByte((byte) i);
        }
        return buf;
    }
}
