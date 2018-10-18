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

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ByteProcessor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.wrappedBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class CompositeByteBufSequentialBenchmark extends AbstractMicrobenchmark {

    public enum ByteBufType {
        SMALL_CHUNKS {
            @Override
            ByteBuf newBuffer(int length) {
                return newBufferSmallChunks(length);
            }
        },
        LARGE_CHUNKS {
            @Override
            ByteBuf newBuffer(int length) {
                return newBufferLargeChunks(length);
            }
        };
        abstract ByteBuf newBuffer(int length);
    }

    @Param({ "8", "64", "1024", "10240", "102400", "1024000" })
    public int size;

    @Param
    public ByteBufType bufferType;

    private ByteBuf buffer;

    @Setup
    public void setup() {
        buffer = bufferType.newBuffer(size);
    }

    @TearDown
    public void teardown() {
        buffer.release();
    }

    private static final ByteProcessor TEST_PROCESSOR = new ByteProcessor() {
        @Override
        public boolean process(byte value) throws Exception {
            return value == 'b'; // false
        }
    };

    @Benchmark
    public int forEachByte() {
        buffer.setIndex(0, buffer.capacity());
        buffer.forEachByte(TEST_PROCESSOR);
        return buffer.forEachByteDesc(TEST_PROCESSOR);
    }

    @Benchmark
    public int sequentialWriteAndRead() {
        buffer.clear();
        for (int i = 0, l = buffer.writableBytes(); i < l; i++) {
            buffer.writeByte('a');
        }
        for (int i = 0, l = buffer.readableBytes(); i < l; i++) {
            if (buffer.readByte() == 'b') {
                return -1;
            }
        }
        return 1;
    }

    private static ByteBuf newBufferSmallChunks(int length) {

        List<ByteBuf> buffers = new ArrayList<ByteBuf>(((length + 1) / 45) * 19);
        for (int i = 0; i < length + 45; i += 45) {
            for (int j = 1; j <= 9; j++) {
                buffers.add(EMPTY_BUFFER);
                buffers.add(wrappedBuffer(new byte[j]));
            }
            buffers.add(EMPTY_BUFFER);
        }

        ByteBuf buffer = wrappedBuffer(Integer.MAX_VALUE, buffers.toArray(new ByteBuf[0]));

        // Truncate to the requested capacity.
        return buffer.capacity(length).writerIndex(0);
    }

    private static ByteBuf newBufferLargeChunks(int length) {

        List<ByteBuf> buffers = new ArrayList<ByteBuf>((length + 1) / 512);
        for (int i = 0; i < length + 1536; i += 1536) {
            buffers.add(wrappedBuffer(new byte[512]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[1024]));
        }

        ByteBuf buffer = wrappedBuffer(Integer.MAX_VALUE, buffers.toArray(new ByteBuf[0]));

        // Truncate to the requested capacity.
        return buffer.capacity(length).writerIndex(0);
    }
}
