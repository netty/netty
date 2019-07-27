/*
 * Copyright 2018 The Netty Project
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
package io.netty.buffer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.netty.microbench.util.AbstractMicrobenchmark;

import static io.netty.buffer.Unpooled.wrappedBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 12, time = 1, timeUnit = TimeUnit.SECONDS)
public class CompositeByteBufWriteOutBenchmark extends AbstractMicrobenchmark {

    public enum ByteBufType {
        SMALL_CHUNKS {
            @Override
            ByteBuf[] sourceBuffers(int length) {
                return makeSmallChunks(length);
            }
        },
        LARGE_CHUNKS {
            @Override
            ByteBuf[] sourceBuffers(int length) {
                return makeLargeChunks(length);
            }
        };
        abstract ByteBuf[] sourceBuffers(int length);
    }

    @Override
    protected String[] jvmArgs() {
        // Ensure we minimize the GC overhead by sizing the heap big enough.
        return new String[] { "-XX:MaxDirectMemorySize=2g", "-Xmx4g", "-Xms4g", "-Xmn3g" };
    }

    @Param({ "64", "1024", "10240", "102400", "1024000" })
    public int size;

    @Param
    public ByteBufType bufferType;

    private ByteBuf targetBuffer;

    private ByteBuf[] sourceBufs;

    @Setup
    public void setup() {
        targetBuffer = PooledByteBufAllocator.DEFAULT.directBuffer(size + 2048);
        sourceBufs = bufferType.sourceBuffers(size);
    }

    @TearDown
    public void teardown() {
        targetBuffer.release();
    }

    @Benchmark
    public int writeCBB() {
        ByteBuf cbb = Unpooled.wrappedBuffer(Integer.MAX_VALUE, sourceBufs); // CompositeByteBuf
        return targetBuffer.clear().writeBytes(cbb).readableBytes();
    }

    @Benchmark
    public int writeFCBB() {
        ByteBuf cbb = Unpooled.wrappedUnmodifiableBuffer(sourceBufs); // FastCompositeByteBuf
        return targetBuffer.clear().writeBytes(cbb).readableBytes();
    }

    private static ByteBuf[] makeSmallChunks(int length) {

        List<ByteBuf> buffers = new ArrayList<ByteBuf>(((length + 1) / 48) * 9);
        for (int i = 0; i < length + 48; i += 48) {
            for (int j = 4; j <= 12; j++) {
                buffers.add(wrappedBuffer(new byte[j]));
            }
        }

        return buffers.toArray(new ByteBuf[0]);
    }

    private static ByteBuf[] makeLargeChunks(int length) {

        List<ByteBuf> buffers = new ArrayList<ByteBuf>((length + 1) / 768);
        for (int i = 0; i < length + 1536; i += 1536) {
            buffers.add(wrappedBuffer(new byte[512]));
            buffers.add(wrappedBuffer(new byte[1024]));
        }

        return buffers.toArray(new ByteBuf[0]);
    }
}
