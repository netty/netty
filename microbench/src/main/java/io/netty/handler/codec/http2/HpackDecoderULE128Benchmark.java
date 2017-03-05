/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Threads(1)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HpackDecoderULE128Benchmark extends AbstractMicrobenchmark {
    private static final Http2Exception DECODE_ULE_128_TO_LONG_DECOMPRESSION_EXCEPTION =
            new Http2Exception(Http2Error.COMPRESSION_ERROR);
    private static final Http2Exception DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION =
            new Http2Exception(Http2Error.COMPRESSION_ERROR);
    private static final Http2Exception DECODE_ULE_128_DECOMPRESSION_EXCEPTION =
            new Http2Exception(Http2Error.COMPRESSION_ERROR);

    private ByteBuf longMaxBuf;
    private ByteBuf intMaxBuf;

    @Setup
    public void setup() {
        byte[] longMax = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                          (byte) 0xFF, (byte) 0x7F};
        longMaxBuf = Unpooled.wrappedBuffer(longMax);
        byte[] intMax = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x07};
        intMaxBuf = Unpooled.wrappedBuffer(intMax);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public long decodeMaxLong() throws Http2Exception {
        long v = decodeULE128(longMaxBuf, 0L);
        longMaxBuf.readerIndex(0);
        return v;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public long decodeMaxIntWithLong() throws Http2Exception {
        long v = decodeULE128(intMaxBuf, 0L);
        intMaxBuf.readerIndex(0);
        return v;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int decodeMaxInt() throws Http2Exception {
        int v = decodeULE128(intMaxBuf, 0);
        intMaxBuf.readerIndex(0);
        return v;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int decodeMaxIntUsingLong() throws Http2Exception {
        int v = decodeULE128UsingLong(intMaxBuf, 0);
        intMaxBuf.readerIndex(0);
        return v;
    }

    static int decodeULE128UsingLong(ByteBuf in, int result) throws Http2Exception {
        final int readerIndex = in.readerIndex();
        final long v = decodeULE128(in, (long) result);
        if (v > Integer.MAX_VALUE) {
            in.readerIndex(readerIndex);
            throw DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION;
        }
        return (int) v;
    }

    static long decodeULE128(ByteBuf in, long result) throws Http2Exception {
        assert result <= 0x7f && result >= 0;
        final boolean resultStartedAtZero = result == 0;
        final int writerIndex = in.writerIndex();
        for (int readerIndex = in.readerIndex(), shift = 0; readerIndex < writerIndex; ++readerIndex, shift += 7) {
            byte b = in.getByte(readerIndex);
            if (shift == 56 && ((b & 0x80) != 0 || b == 0x7F && !resultStartedAtZero)) {
                // the maximum value that can be represented by a signed 64 bit number is:
                // [0x01L, 0x7fL] + 0x7fL + (0x7fL << 7) + (0x7fL << 14) + (0x7fL << 21) + (0x7fL << 28) + (0x7fL << 35)
                // + (0x7fL << 42) + (0x7fL << 49) + (0x7eL << 56)
                // OR
                // 0x0L + 0x7fL + (0x7fL << 7) + (0x7fL << 14) + (0x7fL << 21) + (0x7fL << 28) + (0x7fL << 35) +
                // (0x7fL << 42) + (0x7fL << 49) + (0x7fL << 56)
                // this means any more shifts will result longMaxBuf overflow so we should break out and throw an error.
                throw DECODE_ULE_128_TO_LONG_DECOMPRESSION_EXCEPTION;
            }

            if ((b & 0x80) == 0) {
                in.readerIndex(readerIndex + 1);
                return result + ((b & 0x7FL) << shift);
            }
            result += (b & 0x7FL) << shift;
        }

        throw DECODE_ULE_128_DECOMPRESSION_EXCEPTION;
    }

    static int decodeULE128(ByteBuf in, int result) throws Http2Exception {
        assert result <= 0x7f && result >= 0;
        final boolean resultStartedAtZero = result == 0;
        final int writerIndex = in.writerIndex();
        for (int readerIndex = in.readerIndex(), shift = 0; readerIndex < writerIndex; ++readerIndex, shift += 7) {
            byte b = in.getByte(readerIndex);
            if (shift == 28 && ((b & 0x80) != 0 || !resultStartedAtZero && b > 6 || resultStartedAtZero && b > 7)) {
                // the maximum value that can be represented by a signed 32 bit number is:
                // [0x1,0x7f] + 0x7f + (0x7f << 7) + (0x7f << 14) + (0x7f << 21) + (0x6 << 28)
                // OR
                // 0x0 + 0x7f + (0x7f << 7) + (0x7f << 14) + (0x7f << 21) + (0x7 << 28)
                // this means any more shifts will result longMaxBuf overflow so we should break out and throw an error.
                throw DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION;
            }

            if ((b & 0x80) == 0) {
                in.readerIndex(readerIndex + 1);
                return result + ((b & 0x7F) << shift);
            }
            result += (b & 0x7F) << shift;
        }

        throw DECODE_ULE_128_DECOMPRESSION_EXCEPTION;
    }
}
