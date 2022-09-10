/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static io.netty5.handler.codec.http.HttpConstants.CR;
import static io.netty5.handler.codec.http.HttpConstants.LF;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WriteBytesVsShortOrMediumBenchmark extends AbstractMicrobenchmark {
    private static final short CRLF_SHORT = (CR << 8) + LF;
    private static final byte[] CRLF = { CR, LF };
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) + (CR << 8) + LF;
    private static final byte[] ZERO_CRLF = { '0', CR, LF };

    private final Buffer buf = BufferAllocator.offHeapUnpooled().allocate(16);

    @Benchmark
    public Buffer shortInt() {
        return buf.writeShort(CRLF_SHORT).writerOffset(0);
    }

    @Benchmark
    public Buffer mediumInt() {
        return buf.writeMedium(ZERO_CRLF_MEDIUM).writerOffset(0);
    }

    @Benchmark
    public Buffer byteArray2() {
        return buf.writeBytes(CRLF).writerOffset(0);
    }

    @Benchmark
    public Buffer byteArray3() {
        return buf.writeBytes(ZERO_CRLF).writerOffset(0);
    }

    @Benchmark
    public Buffer chainedBytes2() {
        return buf.writeByte(CR).writeByte(LF).writerOffset(0);
    }

    @Benchmark
    public Buffer chainedBytes3() {
        return buf.writeByte((byte) '0').writeByte(CR).writeByte(LF).writerOffset(0);
    }
}
