/*
 * Copyright 2025 The Netty Project
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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

public class ByteBufCopy2Benchmark extends AbstractMicrobenchmark {
    static {
        System.setProperty("io.netty.buffer.bytebuf.checkAccessible", "false");
    }

    @Param({
            "7",
            "36",
            "128",
            "512",
    })
    private int size;

    @Param({
            "true",
            "false",
    })
    private boolean directByteBuf;

    private ByteBuf buffer1;
    private ByteBuf buffer2;

    @Setup
    public void setup() {
        buffer1 = directByteBuf ?
                ByteBufAllocator.DEFAULT.directBuffer(size, size) :
                ByteBufAllocator.DEFAULT.heapBuffer(size, size);
        buffer2 = directByteBuf ?
                ByteBufAllocator.DEFAULT.directBuffer(size, size) :
                ByteBufAllocator.DEFAULT.heapBuffer(size, size);
        for (int i = 0; i < size; i++) {
            buffer2.setByte(i, 0xA5);
        }
    }

    @Benchmark
    public ByteBuf setBytes() {
        return buffer1.setBytes(0, buffer2, 0, size);
    }

    @TearDown
    public void tearDown() {
        buffer1.release();
        buffer2.release();
    }
}
