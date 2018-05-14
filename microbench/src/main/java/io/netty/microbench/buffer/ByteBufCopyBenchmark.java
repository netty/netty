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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufCopyBenchmark extends AbstractMicrobenchmark {
    static {
        System.setProperty("io.netty.buffer.bytebuf.checkAccessible", "false");
    }

    @Param({"7", "36", "128", "512" })
    private int size;
    @Param({"true", "false" })
    private boolean directByteBuff;
    @Param({"true", "false" })
    private boolean directByteBuffer;
    @Param({"false", "true" })
    private boolean readonlyByteBuffer;
    @Param({"true", "false" })
    private boolean pooledByteBuf;
    @Param({"true", "false" })
    private boolean alignedCopyByteBuffer;
    @Param({"true", "false" })
    private boolean alignedCopyByteBuf;
    @Param({"true", "false" })
    private boolean nativeOrderByteBuffer;

    private ByteBuffer byteBuffer;
    private ByteBuf buffer;
    private int index;

    @Setup
    public void setup() {
        final int requiredByteBufSize = alignedCopyByteBuf ? size : size + 1;
        final int requiredByteBufferSize = alignedCopyByteBuffer ? size : size + 1;
        byteBuffer = directByteBuffer ?
                ByteBuffer.allocateDirect(requiredByteBufferSize) :
                ByteBuffer.allocate(requiredByteBufferSize);
        if (pooledByteBuf) {
            buffer = directByteBuff ?
                    PooledByteBufAllocator.DEFAULT.directBuffer(requiredByteBufSize, requiredByteBufSize) :
                    PooledByteBufAllocator.DEFAULT.heapBuffer(requiredByteBufSize, requiredByteBufSize);
        } else {
            buffer = directByteBuff ?
                    Unpooled.directBuffer(requiredByteBufSize, requiredByteBufSize) :
                    Unpooled.buffer(requiredByteBufSize, requiredByteBufSize);
        }
        if (!alignedCopyByteBuffer) {
            byteBuffer.position(1);
            byteBuffer = byteBuffer.slice();
        }
        if (readonlyByteBuffer) {
            byteBuffer = byteBuffer.asReadOnlyBuffer();
        }
        final ByteOrder byteBufferOrder;
        if (!nativeOrderByteBuffer) {
            byteBufferOrder = ByteOrder.LITTLE_ENDIAN == ByteOrder.nativeOrder() ?
                    ByteOrder.BIG_ENDIAN :
                    ByteOrder.LITTLE_ENDIAN;
        } else {
            byteBufferOrder = ByteOrder.nativeOrder();
        }
        byteBuffer.order(byteBufferOrder);
        index = alignedCopyByteBuf ? 0 : 1;
    }

    @Benchmark
    public ByteBuf setBytes() {
        byteBuffer.clear();
        return buffer.setBytes(index, byteBuffer);
    }

    @TearDown
    public void tearDown() {
        buffer.release();
    }

}
