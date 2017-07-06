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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpConstants.*;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WriteBytesVsShortOrMediumBenchmark extends AbstractMicrobenchmark {
    private static final int CRLF_SHORT = (CR << 8) + LF;
    private static final byte[] CRLF = { CR, LF };
    private static final int ZERO_CRLF_MEDIUM = ('0' << 16) + (CR << 8) + LF;
    private static final byte[] ZERO_CRLF = { '0', CR, LF };

    private final ByteBuf buf = Unpooled.directBuffer(16);

    @Benchmark
    public ByteBuf shortInt() {
        return ByteBufUtil.writeShortBE(buf, CRLF_SHORT).resetWriterIndex();
    }

    @Benchmark
    public ByteBuf mediumInt() {
        return ByteBufUtil.writeMediumBE(buf, ZERO_CRLF_MEDIUM).resetWriterIndex();
    }

    @Benchmark
    public ByteBuf byteArray2() {
        return buf.writeBytes(CRLF).resetWriterIndex();
    }

    @Benchmark
    public ByteBuf byteArray3() {
        return buf.writeBytes(ZERO_CRLF).resetWriterIndex();
    }

    @Benchmark
    public ByteBuf chainedBytes2() {
        return buf.writeByte(CR).writeByte(LF).resetWriterIndex();
    }

    @Benchmark
    public ByteBuf chainedBytes3() {
        return buf.writeByte('0').writeByte(CR).writeByte(LF).resetWriterIndex();
    }
}
