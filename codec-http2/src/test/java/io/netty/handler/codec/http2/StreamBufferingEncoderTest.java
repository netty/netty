/*
 * Copyright 2015 The Netty Project
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

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2Error.CANCEL;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.DefaultMessageSizeEstimator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link StreamBufferingEncoder}.
 */
public class StreamBufferingEncoderTest {

    private StreamBufferingEncoder encoder;

    private Http2Connection connection;

    @Mock
    private Http2FrameWriter writer;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    @Mock
    private Channel.Unsafe unsafe;

    @Mock
    private ChannelConfig config;

    @Mock
    private EventExecutor executor;

    /**
     * Init fields and do mocking.
     */
    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        Http2FrameWriter.Configuration configuration = mock(Http2FrameWriter.Configuration.class);
        Http2FrameSizePolicy frameSizePolicy = mock(Http2FrameSizePolicy.class);
        when(writer.configuration()).thenReturn(configuration);
        when(configuration.frameSizePolicy()).thenReturn(frameSizePolicy);
        when(frameSizePolicy.maxFrameSize()).thenReturn(DEFAULT_MAX_FRAME_SIZE);
        when(writer.writeData(any(ChannelHandlerContext.class), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean(),
                any(ChannelPromise.class))).thenAnswer(successAnswer());
        when(writer.writeRstStream(eq(ctx), anyInt(), anyLong(), any(ChannelPromise.class))).thenAnswer(
                successAnswer());
        when(writer.writeGoAway(any(ChannelHandlerContext.class), anyInt(), anyLong(), any(ByteBuf.class),
                any(ChannelPromise.class)))
                .thenAnswer(successAnswer());

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
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(channel.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(executor.inEventLoop()).thenReturn(true);
        doAnswer(new Answer<ChannelPromise>() {
            @Override
            public ChannelPromise answer(InvocationOnMock invocation) throws Throwable {
                return newPromise();
            }
        }).when(ctx).newPromise();
        when(ctx.executor()).thenReturn(executor);
        when(channel.isActive()).thenReturn(false);
        when(channel.config()).thenReturn(config);
        when(channel.isWritable()).thenReturn(true);
        when(channel.bytesBeforeUnwritable()).thenReturn(Long.MAX_VALUE);
        when(config.getWriteBufferHighWaterMark()).thenReturn(Integer.MAX_VALUE);
        when(config.getMessageSizeEstimator()).thenReturn(DefaultMessageSizeEstimator.DEFAULT);
        ChannelMetadata metadata = new ChannelMetadata(false, 16);
        when(channel.metadata()).thenReturn(metadata);
        when(channel.unsafe()).thenReturn(unsafe);
        handler.handlerAdded(ctx);
    }

    @After
    public void teardown() {
        // Close and release any buffered frames.
        encoder.close();
    }

    @Test
    public void multipleWritesToActiveStream() {
        encoder.writeSettingsAck(ctx, newPromise());
        encoderWriteHeaders(3, newPromise());
        assertEquals(0, encoder.numBufferedStreams());
        ByteBuf data = data();
        final int expectedBytes = data.readableBytes() * 3;
        encoder.writeData(ctx, 3, data, 0, false, newPromise());
        encoder.writeData(ctx, 3, data(), 0, false, newPromise());
        encoder.writeData(ctx, 3, data(), 0, false, newPromise());
        encoderWriteHeaders(3, newPromise());

        writeVerifyWriteHeaders(times(2), 3);
        // Contiguous data writes are coalesced
        ArgumentCaptor<ByteBuf> bufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(writer, times(1))
                .writeData(eq(ctx), eq(3), bufCaptor.capture(), eq(0), eq(false), any(ChannelPromise.class));
        assertEquals(expectedBytes, bufCaptor.getValue().readableBytes());
    }

    @Test
    public void ensureCanCreateNextStreamWhenStreamCloses() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, newPromise());
        assertEquals(0, encoder.numBufferedStreams());

        // This one gets buffered.
        encoderWriteHeaders(5, newPromise());
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
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, newPromise());
        assertEquals(0, encoder.numBufferedStreams());

        encoderWriteHeaders(5, newPromise());
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeData(ctx, 3, EMPTY_BUFFER, 0, false, newPromise());
        writeVerifyWriteHeaders(times(1), 3);
        encoder.writeData(ctx, 5, EMPTY_BUFFER, 0, false, newPromise());
        verify(writer, never())
                .writeData(eq(ctx), eq(5), any(ByteBuf.class), eq(0), eq(false), eq(newPromise()));
    }

    @Test
    public void bufferingNewStreamFailsAfterGoAwayReceived() throws Http2Exception {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(0);
        connection.goAwayReceived(1, 8, EMPTY_BUFFER);

        ChannelPromise promise = newPromise();
        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());
        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
    }

    @Test
    public void receivingGoAwayFailsBufferedStreams() throws Http2Exception {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(5);

        int streamId = 3;
        List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
        for (int i = 0; i < 9; i++) {
            futures.add(encoderWriteHeaders(streamId, newPromise()));
            streamId += 2;
        }
        assertEquals(4, encoder.numBufferedStreams());

        connection.goAwayReceived(11, 8, EMPTY_BUFFER);

        assertEquals(5, connection.numActiveStreams());
        int failCount = 0;
        for (ChannelFuture f : futures) {
            if (f.cause() != null) {
                failCount++;
            }
        }
        assertEquals(9, failCount);
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void sendingGoAwayShouldNotFailStreams() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(1);

        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
              anyBoolean(), any(ChannelPromise.class))).thenAnswer(successAnswer());
        when(writer.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
              anyShort(), anyBoolean(), anyInt(), anyBoolean(), any(ChannelPromise.class))).thenAnswer(successAnswer());

        ChannelFuture f1 = encoderWriteHeaders(3, newPromise());
        assertEquals(0, encoder.numBufferedStreams());
        ChannelFuture f2 = encoderWriteHeaders(5, newPromise());
        assertEquals(1, encoder.numBufferedStreams());
        ChannelFuture f3 = encoderWriteHeaders(7, newPromise());
        assertEquals(2, encoder.numBufferedStreams());

        ByteBuf empty = Unpooled.buffer(0);
        encoder.writeGoAway(ctx, 3, CANCEL.code(), empty, newPromise());

        assertEquals(1, connection.numActiveStreams());
        assertEquals(2, encoder.numBufferedStreams());
        assertFalse(f1.isDone());
        assertFalse(f2.isDone());
        assertFalse(f3.isDone());
    }

    @Test
    public void endStreamDoesNotFailBufferedStream() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3, newPromise());
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeData(ctx, 3, EMPTY_BUFFER, 0, true, newPromise());

        assertEquals(0, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 1.
        setMaxConcurrentStreams(1);
        encoder.writeSettingsAck(ctx, newPromise());

        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
        assertEquals(HALF_CLOSED_LOCAL, connection.stream(3).state());
    }

    @Test
    public void rstStreamClosesBufferedStream() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3, newPromise());
        assertEquals(1, encoder.numBufferedStreams());

        ChannelPromise rstStreamPromise = newPromise();
        encoder.writeRstStream(ctx, 3, CANCEL.code(), rstStreamPromise);
        assertTrue(rstStreamPromise.isSuccess());
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilActiveStreamsAreReset() throws Exception {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, newPromise());
        assertEquals(0, encoder.numBufferedStreams());
        encoderWriteHeaders(5, newPromise());
        assertEquals(1, encoder.numBufferedStreams());
        encoderWriteHeaders(7, newPromise());
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3);
        writeVerifyWriteHeaders(never(), 5);
        writeVerifyWriteHeaders(never(), 7);

        encoder.writeRstStream(ctx, 3, CANCEL.code(), newPromise());
        connection.remote().flowController().writePendingBytes();
        writeVerifyWriteHeaders(times(1), 5);
        writeVerifyWriteHeaders(never(), 7);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeRstStream(ctx, 5, CANCEL.code(), newPromise());
        connection.remote().flowController().writePendingBytes();
        writeVerifyWriteHeaders(times(1), 7);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());

        encoder.writeRstStream(ctx, 7, CANCEL.code(), newPromise());
        assertEquals(0, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilMaxStreamsIncreased() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(2);

        encoderWriteHeaders(3, newPromise());
        encoderWriteHeaders(5, newPromise());
        encoderWriteHeaders(7, newPromise());
        encoderWriteHeaders(9, newPromise());
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3);
        writeVerifyWriteHeaders(times(1), 5);
        writeVerifyWriteHeaders(never(), 7);
        writeVerifyWriteHeaders(never(), 9);

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 5.
        setMaxConcurrentStreams(5);
        encoder.writeSettingsAck(ctx, newPromise());

        assertEquals(0, encoder.numBufferedStreams());
        writeVerifyWriteHeaders(times(1), 7);
        writeVerifyWriteHeaders(times(1), 9);

        encoderWriteHeaders(11, newPromise());

        writeVerifyWriteHeaders(times(1), 11);

        assertEquals(5, connection.local().numActiveStreams());
    }

    @Test
    public void bufferUntilSettingsReceived() throws Http2Exception {
        int initialLimit = SMALLEST_MAX_CONCURRENT_STREAMS;
        int numStreams = initialLimit * 2;
        for (int ix = 0, nextStreamId = 3; ix < numStreams; ++ix, nextStreamId += 2) {
            encoderWriteHeaders(nextStreamId, newPromise());
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
            encoderWriteHeaders(nextStreamId, newPromise());
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
    public void exhaustedStreamsDoNotBuffer() throws Http2Exception {
        // Write the highest possible stream ID for the client.
        // This will cause the next stream ID to be negative.
        encoderWriteHeaders(Integer.MAX_VALUE, newPromise());

        // Disallow any further streams.
        setMaxConcurrentStreams(0);

        // Simulate numeric overflow for the next stream ID.
        ChannelFuture f = encoderWriteHeaders(-1, newPromise());

        // Verify that the write fails.
        assertNotNull(f.cause());
    }

    @Test
    public void closedBufferedStreamReleasesByteBuf() {
        encoder.writeSettingsAck(ctx, newPromise());
        setMaxConcurrentStreams(0);
        ByteBuf data = mock(ByteBuf.class);
        ChannelFuture f1 = encoderWriteHeaders(3, newPromise());
        assertEquals(1, encoder.numBufferedStreams());
        ChannelFuture f2 = encoder.writeData(ctx, 3, data, 0, false, newPromise());

        ChannelPromise rstPromise = mock(ChannelPromise.class);
        encoder.writeRstStream(ctx, 3, CANCEL.code(), rstPromise);

        assertEquals(0, encoder.numBufferedStreams());
        verify(rstPromise).setSuccess();
        assertTrue(f1.isSuccess());
        assertTrue(f2.isSuccess());
        verify(data).release();
    }

    @Test
    public void closeShouldCancelAllBufferedStreams() throws Http2Exception {
        encoder.writeSettingsAck(ctx, newPromise());
        connection.local().maxActiveStreams(0);

        ChannelFuture f1 = encoderWriteHeaders(3, newPromise());
        ChannelFuture f2 = encoderWriteHeaders(5, newPromise());
        ChannelFuture f3 = encoderWriteHeaders(7, newPromise());

        encoder.close();
        assertNotNull(f1.cause());
        assertNotNull(f2.cause());
        assertNotNull(f3.cause());
    }

    @Test
    public void headersAfterCloseShouldImmediatelyFail() {
        encoder.writeSettingsAck(ctx, newPromise());
        encoder.close();

        ChannelFuture f = encoderWriteHeaders(3, newPromise());
        assertNotNull(f.cause());
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

    private ChannelFuture encoderWriteHeaders(int streamId, ChannelPromise promise) {
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers(), 0, DEFAULT_PRIORITY_WEIGHT,
                             false, 0, false, promise);
        try {
            encoder.flowController().writePendingBytes();
            return promise;
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeVerifyWriteHeaders(VerificationMode mode, int streamId) {
        verify(writer, mode).writeHeaders(eq(ctx), eq(streamId), any(Http2Headers.class), eq(0),
                                          eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0),
                                          eq(false), any(ChannelPromise.class));
    }

    private Answer<ChannelFuture> successAnswer() {
        return new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                for (Object a : invocation.getArguments()) {
                    ReferenceCountUtil.safeRelease(a);
                }

                ChannelPromise future = newPromise();
                future.setSuccess();
                return future;
            }
        };
    }

    private ChannelPromise newPromise() {
        return new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
    }

    private static ByteBuf data() {
        ByteBuf buf = Unpooled.buffer(10);
        for (int i = 0; i < buf.writableBytes(); i++) {
            buf.writeByte(i);
        }
        return buf;
    }
}
