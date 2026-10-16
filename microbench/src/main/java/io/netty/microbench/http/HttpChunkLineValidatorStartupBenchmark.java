/*
 * Copyright 2026 The Netty Project
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
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures the startup cost of processing the first chunked HTTP request,
 * which triggers class loading and static initialization of
 * {@code HttpChunkLineValidatingByteProcessor} (builds the transition table).
 * <p>
 * Uses SingleShotTime with many forks so each measurement is a fresh JVM
 * with cold class loading.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 30)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
public class HttpChunkLineValidatorStartupBenchmark extends AbstractMicrobenchmark {

    private ByteBuf request;
    private EmbeddedChannel channel;

    @Setup(Level.Trial)
    public void setup() {
        channel = new EmbeddedChannel(new HttpRequestDecoder(
                HttpRequestDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH, HttpRequestDecoder.DEFAULT_MAX_HEADER_SIZE,
                HttpRequestDecoder.DEFAULT_MAX_CHUNK_SIZE, false));

        ByteBuf buffer = Unpooled.buffer();
        ByteBufUtil.writeAscii(buffer, "POST / HTTP/1.1\r\n");
        ByteBufUtil.writeAscii(buffer, "Transfer-Encoding: chunked\r\n\r\n");
        ByteBufUtil.writeAscii(buffer, "a;ext=value\r\n");
        buffer.writeZero(10);
        ByteBufUtil.writeAscii(buffer, "\r\n0\r\n\r\n");
        request = Unpooled.unreleasableBuffer(buffer);
    }

    @Benchmark
    public void firstChunkedRequest() {
        request.resetReaderIndex();
        channel.writeInbound(request.retainedDuplicate());
        Object msg;
        while ((msg = channel.readInbound()) != null) {
            ReferenceCountUtil.release(msg);
        }
    }
}
