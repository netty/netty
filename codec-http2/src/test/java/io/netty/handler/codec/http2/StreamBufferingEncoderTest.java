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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.http2.StreamBufferingEncoder.GoAwayException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
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
    private ChannelPromise promise;

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
        when(writer.writeData(eq(ctx), anyInt(), any(ByteBuf.class), anyInt(), anyBoolean(),
                eq(promise))).thenAnswer(successAnswer());
        when(writer.writeRstStream(eq(ctx), anyInt(), anyLong(), eq(promise))).thenAnswer(
                successAnswer());
        when(writer.writeGoAway(eq(ctx), anyInt(), anyLong(), any(ByteBuf.class),
                any(ChannelPromise.class)))
                .thenAnswer(successAnswer());

        connection = new DefaultHttp2Connection(false);

        DefaultHttp2ConnectionEncoder defaultEncoder =
                new DefaultHttp2ConnectionEncoder(connection, writer);
        encoder = new StreamBufferingEncoder(defaultEncoder);
        DefaultHttp2ConnectionDecoder decoder =
                new DefaultHttp2ConnectionDecoder(connection, encoder,
                        mock(Http2FrameReader.class), mock(Http2FrameListener.class));

        Http2ConnectionHandler handler = new Http2ConnectionHandler(decoder, encoder);
        // Set LifeCycleManager on encoder and decoder
        when(ctx.channel()).thenReturn(channel);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.newPromise()).thenReturn(promise);
        when(channel.isActive()).thenReturn(false);
        handler.handlerAdded(ctx);
    }

    @After
    public void teardown() {
        // Close and release any buffered frames.
        encoder.close();
    }

    @Test
    public void multipleWritesToActiveStream() {
        encoder.writeSettingsAck(ctx, promise);
        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());
        encoder.writeData(ctx, 3, data(), 0, false, promise);
        encoder.writeData(ctx, 3, data(), 0, false, promise);
        encoder.writeData(ctx, 3, data(), 0, false, promise);
        encoderWriteHeaders(3, promise);

        writeVerifyWriteHeaders(times(2), 3, promise);
        // Contiguous data writes are coalesced
        ArgumentCaptor<ByteBuf> bufCaptor = ArgumentCaptor.forClass(ByteBuf.class);
        verify(writer, times(1))
                .writeData(eq(ctx), eq(3), bufCaptor.capture(), eq(0), eq(false), eq(promise));
        assertEquals(data().readableBytes() * 3, bufCaptor.getValue().readableBytes());
    }

    @Test
    public void ensureCanCreateNextStreamWhenStreamCloses() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());

        // This one gets buffered.
        encoderWriteHeaders(5, promise);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        // Now prevent us from creating another stream.
        setMaxConcurrentStreams(0);

        // Close the previous stream.
        connection.stream(3).close();

        // Ensure that no streams are currently active and that only the HEADERS from the first
        // stream were written.
        writeVerifyWriteHeaders(times(1), 3, promise);
        writeVerifyWriteHeaders(never(), 5, promise);
        assertEquals(0, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());
    }

    @Test
    public void alternatingWritesToActiveAndBufferedStreams() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());

        encoderWriteHeaders(5, promise);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeData(ctx, 3, EMPTY_BUFFER, 0, false, promise);
        writeVerifyWriteHeaders(times(1), 3, promise);
        encoder.writeData(ctx, 5, EMPTY_BUFFER, 0, false, promise);
        verify(writer, never())
                .writeData(eq(ctx), eq(5), any(ByteBuf.class), eq(0), eq(false), eq(promise));
    }

    @Test
    public void bufferingNewStreamFailsAfterGoAwayReceived() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(0);
        connection.goAwayReceived(1, 8, null);

        promise = mock(ChannelPromise.class);
        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());
        verify(promise).setFailure(any(Throwable.class));
    }

    @Test
    public void receivingGoAwayFailsBufferedStreams() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(5);

        int streamId = 3;
        for (int i = 0; i < 9; i++) {
            encoderWriteHeaders(streamId, promise);
            streamId += 2;
        }
        assertEquals(4, encoder.numBufferedStreams());

        connection.goAwayReceived(11, 8, null);

        assertEquals(5, connection.numActiveStreams());
        // The 4 buffered streams must have been failed.
        verify(promise, times(4)).setFailure(any(Throwable.class));
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void sendingGoAwayShouldNotFailStreams() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());
        encoderWriteHeaders(5, promise);
        assertEquals(1, encoder.numBufferedStreams());
        encoderWriteHeaders(7, promise);
        assertEquals(2, encoder.numBufferedStreams());

        ByteBuf empty = Unpooled.buffer(0);
        encoder.writeGoAway(ctx, 3, CANCEL.code(), empty, promise);

        assertEquals(1, connection.numActiveStreams());
        assertEquals(2, encoder.numBufferedStreams());
        verify(promise, never()).setFailure(any(GoAwayException.class));
    }

    @Test
    public void endStreamDoesNotFailBufferedStream() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3, promise);
        assertEquals(1, encoder.numBufferedStreams());

        encoder.writeData(ctx, 3, EMPTY_BUFFER, 0, true, promise);

        assertEquals(0, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 1.
        setMaxConcurrentStreams(1);
        encoder.writeSettingsAck(ctx, promise);

        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
        assertEquals(HALF_CLOSED_LOCAL, connection.stream(3).state());
    }

    @Test
    public void rstStreamClosesBufferedStream() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(0);

        encoderWriteHeaders(3, promise);
        assertEquals(1, encoder.numBufferedStreams());

        verify(promise, never()).setSuccess();
        ChannelPromise rstStreamPromise = mock(ChannelPromise.class);
        encoder.writeRstStream(ctx, 3, CANCEL.code(), rstStreamPromise);
        verify(promise).setSuccess();
        verify(rstStreamPromise).setSuccess();
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilActiveStreamsAreReset() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(1);

        encoderWriteHeaders(3, promise);
        assertEquals(0, encoder.numBufferedStreams());
        encoderWriteHeaders(5, promise);
        assertEquals(1, encoder.numBufferedStreams());
        encoderWriteHeaders(7, promise);
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3, promise);
        writeVerifyWriteHeaders(never(), 5, promise);
        writeVerifyWriteHeaders(never(), 7, promise);

        encoder.writeRstStream(ctx, 3, CANCEL.code(), promise);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(1, encoder.numBufferedStreams());
        encoder.writeRstStream(ctx, 5, CANCEL.code(), promise);
        assertEquals(1, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
        encoder.writeRstStream(ctx, 7, CANCEL.code(), promise);
        assertEquals(0, connection.numActiveStreams());
        assertEquals(0, encoder.numBufferedStreams());
    }

    @Test
    public void bufferUntilMaxStreamsIncreased() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(2);

        encoderWriteHeaders(3, promise);
        encoderWriteHeaders(5, promise);
        encoderWriteHeaders(7, promise);
        encoderWriteHeaders(9, promise);
        assertEquals(2, encoder.numBufferedStreams());

        writeVerifyWriteHeaders(times(1), 3, promise);
        writeVerifyWriteHeaders(times(1), 5, promise);
        writeVerifyWriteHeaders(never(), 7, promise);
        writeVerifyWriteHeaders(never(), 9, promise);

        // Simulate that we received a SETTINGS frame which
        // increased MAX_CONCURRENT_STREAMS to 5.
        setMaxConcurrentStreams(5);
        encoder.writeSettingsAck(ctx, promise);

        assertEquals(0, encoder.numBufferedStreams());
        writeVerifyWriteHeaders(times(1), 7, promise);
        writeVerifyWriteHeaders(times(1), 9, promise);

        encoderWriteHeaders(11, promise);

        writeVerifyWriteHeaders(times(1), 11, promise);

        assertEquals(5, connection.local().numActiveStreams());
    }

    @Test
    public void bufferUntilSettingsReceived() throws Http2Exception {
        int initialLimit = SMALLEST_MAX_CONCURRENT_STREAMS;
        int numStreams = initialLimit * 2;
        for (int ix = 0, nextStreamId = 3; ix < numStreams; ++ix, nextStreamId += 2) {
            encoderWriteHeaders(nextStreamId, promise);
            if (ix < initialLimit) {
                writeVerifyWriteHeaders(times(1), nextStreamId, promise);
            } else {
                writeVerifyWriteHeaders(never(), nextStreamId, promise);
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
            encoderWriteHeaders(nextStreamId, promise);
            if (ix < initialLimit) {
                writeVerifyWriteHeaders(times(1), nextStreamId, promise);
            } else {
                writeVerifyWriteHeaders(never(), nextStreamId, promise);
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
        encoderWriteHeaders(Integer.MAX_VALUE, promise);

        // Disallow any further streams.
        setMaxConcurrentStreams(0);

        // Simulate numeric overflow for the next stream ID.
        encoderWriteHeaders(-1, promise);

        // Verify that the write fails.
        verify(promise).setFailure(any(Http2Exception.class));
    }

    @Test
    public void closedBufferedStreamReleasesByteBuf() {
        encoder.writeSettingsAck(ctx, promise);
        setMaxConcurrentStreams(0);
        ByteBuf data = mock(ByteBuf.class);
        encoderWriteHeaders(3, promise);
        assertEquals(1, encoder.numBufferedStreams());
        encoder.writeData(ctx, 3, data, 0, false, promise);

        ChannelPromise rstPromise = mock(ChannelPromise.class);
        encoder.writeRstStream(ctx, 3, CANCEL.code(), rstPromise);

        assertEquals(0, encoder.numBufferedStreams());
        verify(rstPromise).setSuccess();
        verify(promise, times(2)).setSuccess();
        verify(data).release();
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

    private void encoderWriteHeaders(int streamId, ChannelPromise promise) {
        encoder.writeHeaders(ctx, streamId, new DefaultHttp2Headers(), 0, DEFAULT_PRIORITY_WEIGHT,
                false, 0, false, promise);
        try {
            encoder.flowController().writePendingBytes();
        } catch (Http2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeVerifyWriteHeaders(VerificationMode mode, int streamId,
                                         ChannelPromise promise) {
        verify(writer, mode).writeHeaders(eq(ctx), eq(streamId), any(Http2Headers.class), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0),
                eq(false), eq(promise));
    }

    private Answer<ChannelFuture> successAnswer() {
        return new Answer<ChannelFuture>() {
            @Override
            public ChannelFuture answer(InvocationOnMock invocation) throws Throwable {
                for (Object a : invocation.getArguments()) {
                    ReferenceCountUtil.safeRelease(a);
                }

                ChannelPromise future =
                        new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
                future.setSuccess();
                return future;
            }
        };
    }

    private static ByteBuf data() {
        ByteBuf buf = Unpooled.buffer(10);
        for (int i = 0; i < buf.writableBytes(); i++) {
            buf.writeByte(i);
        }
        return buf;
    }
}
