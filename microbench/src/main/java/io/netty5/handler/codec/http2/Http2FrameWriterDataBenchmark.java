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
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty5.microbench.util.AbstractMicrobenchmark;
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

import static io.netty5.buffer.BufferAllocator.onHeapPooled;
import static io.netty5.buffer.BufferAllocator.onHeapUnpooled;

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

    private Buffer payload;
    private ChannelHandlerContext ctx;
    private Http2DataWriter writer;
    private BufferAllocator allocator;

    @Setup(Level.Trial)
    public void setup() {
        writer = new DefaultHttp2FrameWriter();
        allocator = pooled ? onHeapPooled() : onHeapUnpooled();
        payload = allocator.allocate(payloadSize);
        for (int i = 0; i < payloadSize; i++) {
            payload.writeByte((byte) 0);
        }
        payload.makeReadOnly();
        ctx = new EmbeddedChannelWriteReleaseHandlerContext(allocator, new ChannelHandler() { }) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (payload != null) {
            payload.close();
        }
        if (ctx != null) {
            ctx.close();
        }
        allocator.close();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void newWriter() {
        writer.writeData(ctx, 3, payload.copy(true), padding, true);
        ctx.flush();
    }
}
