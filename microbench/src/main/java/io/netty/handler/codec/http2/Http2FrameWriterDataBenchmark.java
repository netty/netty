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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.DATA_FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_BYTE;
import static io.netty.handler.codec.http2.Http2CodecUtil.verifyPadding;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeFrameHeaderInternal;
import static io.netty.handler.codec.http2.Http2FrameTypes.DATA;
import static java.lang.Math.max;
import static java.lang.Math.min;

@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class Http2FrameWriterDataBenchmark extends AbstractMicrobenchmark {
    @Param({ "64", "1024", "4096", "16384", "1048576", "4194304" })
    public int payloadSize;

    @Param({ "0", "100", "255" })
    public int padding;

    @Param({ "true", "false" })
    public boolean pooled;

    private ByteBuf payload;
    private ChannelHandlerContext ctx;
    private Http2DataWriter writer;
    private Http2DataWriter oldWriter;

    @Setup(Level.Trial)
    public void setup() {
        writer = new DefaultHttp2FrameWriter();
        oldWriter = new OldDefaultHttp2FrameWriter();
        payload = pooled ? PooledByteBufAllocator.DEFAULT.buffer(payloadSize) : Unpooled.buffer(payloadSize);
        payload.writeZero(payloadSize);
        ctx = new EmbeddedChannelWriteReleaseHandlerContext(
                pooled ? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT,
                new ChannelInboundHandlerAdapter()) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (payload != null) {
            payload.release();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void newWriter() {
        writer.writeData(ctx, 3, payload.retain(), padding, true, ctx.voidPromise());
        ctx.flush();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void oldWriter() {
        oldWriter.writeData(ctx, 3, payload.retain(), padding, true, ctx.voidPromise());
        ctx.flush();
    }

    private static final class OldDefaultHttp2FrameWriter implements Http2DataWriter {
        private static final ByteBuf ZERO_BUFFER =
                unreleasableBuffer(directBuffer(MAX_UNSIGNED_BYTE).writeZero(MAX_UNSIGNED_BYTE)).asReadOnly();
        private int maxFrameSize = DEFAULT_MAX_FRAME_SIZE;
        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endStream, ChannelPromise promise) {
            final Http2CodecUtil.SimpleChannelPromiseAggregator promiseAggregator =
                    new Http2CodecUtil.SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
            final DataFrameHeader header = new DataFrameHeader(ctx, streamId);
            boolean needToReleaseHeaders = true;
            boolean needToReleaseData = true;
            try {
                verifyStreamId(streamId, "Stream ID");
                verifyPadding(padding);

                boolean lastFrame;
                int remainingData = data.readableBytes();
                do {
                    // Determine how much data and padding to write in this frame. Put all padding at the end.
                    int frameDataBytes = min(remainingData, maxFrameSize);
                    int framePaddingBytes = min(padding, max(0, (maxFrameSize - 1) - frameDataBytes));

                    // Decrement the remaining counters.
                    padding -= framePaddingBytes;
                    remainingData -= frameDataBytes;

                    // Determine whether or not this is the last frame to be sent.
                    lastFrame = remainingData == 0 && padding == 0;

                    // Only the last frame is not retained. Until then, the outer finally must release.
                    ByteBuf frameHeader = header.slice(frameDataBytes, framePaddingBytes, lastFrame && endStream);
                    needToReleaseHeaders = !lastFrame;
                    ctx.write(lastFrame ? frameHeader : frameHeader.retain(), promiseAggregator.newPromise());

                    // Write the frame data.
                    ByteBuf frameData = data.readSlice(frameDataBytes);
                    // Only the last frame is not retained. Until then, the outer finally must release.
                    needToReleaseData = !lastFrame;
                    ctx.write(lastFrame ? frameData : frameData.retain(), promiseAggregator.newPromise());

                    // Write the frame padding.
                    if (paddingBytes(framePaddingBytes) > 0) {
                        ctx.write(ZERO_BUFFER.slice(0, paddingBytes(framePaddingBytes)),
                                promiseAggregator.newPromise());
                    }
                } while (!lastFrame);
            } catch (Throwable t) {
                try {
                    if (needToReleaseHeaders) {
                        header.release();
                    }
                    if (needToReleaseData) {
                        data.release();
                    }
                } finally {
                    promiseAggregator.setFailure(t);
                    promiseAggregator.doneAllocatingPromises();
                }
                return promiseAggregator;
            }
            return promiseAggregator.doneAllocatingPromises();
        }

        private static void verifyStreamId(int streamId, String argumentName) {
            if (streamId <= 0) {
                throw new IllegalArgumentException(argumentName + " must be > 0");
            }
        }

        private static int paddingBytes(int padding) {
            // The padding parameter contains the 1 byte pad length field as well as the trailing padding bytes.
            // Subtract 1, so to only get the number of padding bytes that need to be appended to the end of a frame.
            return padding - 1;
        }

        private static void writePaddingLength(ByteBuf buf, int padding) {
            if (padding > 0) {
                // It is assumed that the padding length has been bounds checked before this
                // Minus 1, as the pad length field is included in the padding parameter and is 1 byte wide.
                buf.writeByte(padding - 1);
            }
        }

        /**
         * Utility class that manages the creation of frame header buffers for {@code DATA} frames. Attempts
         * to reuse the same buffer repeatedly when splitting data into multiple frames.
         */
        private static final class DataFrameHeader {
            private final int streamId;
            private final ByteBuf buffer;
            private final Http2Flags flags = new Http2Flags();
            private int prevData;
            private int prevPadding;
            private ByteBuf frameHeader;

            DataFrameHeader(ChannelHandlerContext ctx, int streamId) {
                // All padding will be put at the end, so in the worst case we need 3 headers:
                // a repeated no-padding frame of maxFrameSize, a frame that has part data and part
                // padding, and a frame that has the remainder of the padding.
                buffer = ctx.alloc().buffer(3 * DATA_FRAME_HEADER_LENGTH);
                this.streamId = streamId;
            }

            /**
             * Gets the frame header buffer configured for the current frame.
             */
            ByteBuf slice(int data, int padding, boolean endOfStream) {
                // Since we're reusing the current frame header whenever possible, check if anything changed
                // that requires a new header.
                if (data != prevData || padding != prevPadding
                        || endOfStream != flags.endOfStream() || frameHeader == null) {
                    // Update the header state.
                    prevData = data;
                    prevPadding = padding;
                    flags.paddingPresent(padding > 0);
                    flags.endOfStream(endOfStream);
                    frameHeader = buffer.slice(buffer.readerIndex(), DATA_FRAME_HEADER_LENGTH).writerIndex(0);
                    buffer.setIndex(buffer.readerIndex() + DATA_FRAME_HEADER_LENGTH,
                            buffer.writerIndex() + DATA_FRAME_HEADER_LENGTH);

                    int payloadLength = data + padding;
                    writeFrameHeaderInternal(frameHeader, payloadLength, DATA, flags, streamId);
                    writePaddingLength(frameHeader, padding);
                }
                return frameHeader.slice();
            }

            void release() {
                buffer.release();
            }
        }
    }
}
