/*
 * Copyright 2015 The Netty Project
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

import java.lang.reflect.Constructor;

public class HeapByteBufBenchmark extends AbstractMicrobenchmark {

    @Param({
            "true",
            "false",
    })
    public String checkBounds;

    private ByteBuf unsafeBuffer;
    private ByteBuf buffer;

    private static ByteBuf newBuffer(String classname) throws Exception {
        // Using reflection to workaround package-private implementations.
        Class<?> clazz = Class.forName(classname);
        Constructor<?> constructor = clazz.getDeclaredConstructor(ByteBufAllocator.class, int.class, int.class);
        constructor.setAccessible(true);
        return (ByteBuf) constructor.newInstance(ByteBufAllocator.DEFAULT, 8, Integer.MAX_VALUE);
    }

    @Setup
    public void setup() throws Exception {
        System.setProperty("io.netty.buffer.bytebuf.checkBounds", checkBounds);
        unsafeBuffer = newBuffer("io.netty.buffer.UnpooledUnsafeHeapByteBuf");
        buffer = newBuffer("io.netty.buffer.UnpooledHeapByteBuf");
        unsafeBuffer.writeLong(1L);
        buffer.writeLong(1L);
    }

    @TearDown
    public void destroy() {
        unsafeBuffer.release();
        buffer.release();
    }

    @Benchmark
    public byte getByteUnsafe() {
        return unsafeBuffer.getByte(0);
    }

    @Benchmark
    public short getByte() {
        return buffer.getByte(0);
    }

    @Benchmark
    public short getShortUnsafe() {
        return unsafeBuffer.getShort(0);
    }

    @Benchmark
    public short getShort() {
        return buffer.getShort(0);
    }

    @Benchmark
    public int getMediumUnsafe() {
        return unsafeBuffer.getMedium(0);
    }

    @Benchmark
    public int getMedium() {
        return buffer.getMedium(0);
    }

    @Benchmark
    public int getIntUnsafe() {
        return unsafeBuffer.getInt(0);
    }

    @Benchmark
    public int getInt() {
        return buffer.getInt(0);
    }

    @Benchmark
    public long getLongUnsafe() {
        return unsafeBuffer.getLong(0);
    }

    @Benchmark
    public long getLong() {
        return buffer.getLong(0);
    }
}
