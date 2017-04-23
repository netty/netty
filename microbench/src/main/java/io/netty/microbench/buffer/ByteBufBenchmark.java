/*
* Copyright 2015 The Netty Project
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;

public class ByteBufBenchmark extends AbstractMicrobenchmark {
    static {
        System.setProperty("io.netty.buffer.bytebuf.checkAccessible", "false");
    }
    private static final byte BYTE = '0';

    private ByteBuffer byteBuffer;
    private ByteBuffer directByteBuffer;
    private ByteBuf buffer;
    private ByteBuf directBuffer;
    private ByteBuf directBufferPooled;

    @Setup
    public void setup() {
        byteBuffer = ByteBuffer.allocate(8);
        directByteBuffer = ByteBuffer.allocateDirect(8);
        buffer = Unpooled.buffer(8);
        directBuffer = Unpooled.directBuffer(8);
        directBufferPooled = PooledByteBufAllocator.DEFAULT.directBuffer(8);
    }

    @TearDown
    public void tearDown() {
        buffer.release();
        directBuffer.release();
        directBufferPooled.release();
    }

    @Benchmark
    public void setByteBufferHeap() {
        byteBuffer.put(0, BYTE);
    }

    @Benchmark
    public void setByteBufferDirect() {
        directByteBuffer.put(0, BYTE);
    }

    @Benchmark
    public void setByteBufHeap() {
        buffer.setByte(0, BYTE);
    }

    @Benchmark
    public void setByteBufDirect() {
        directBuffer.setByte(0, BYTE);
    }

    @Benchmark
    public void setByteBufDirectPooled() {
        directBufferPooled.setByte(0, BYTE);
    }
}
