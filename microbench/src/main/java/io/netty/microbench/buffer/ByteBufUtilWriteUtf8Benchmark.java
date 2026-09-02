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
package io.netty.microbench.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Arrays;

@State(Scope.Thread)
public class ByteBufUtilWriteUtf8Benchmark extends AbstractMicrobenchmark {

    @Param({
            "default",
            "unpooledHeap",
    })
    public String allocatorType;

    @Param({
            "1024",
            "4096",
            "16384",
            "65536",
    })
    public int length;

    private ByteBufAllocator allocator;
    private AsciiString sequence;

    @Setup
    public void setup() {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        sequence = new AsciiString(bytes, false);

        if ("default".equals(allocatorType)) {
            allocator = ByteBufAllocator.DEFAULT;
        } else if ("unpooledHeap".equals(allocatorType)) {
            allocator = new UnpooledByteBufAllocator(false, true);
        } else {
            throw new IllegalArgumentException("unknown allocator type: " + allocatorType);
        }
    }

    @Benchmark
    public int writeUtf8() {
        ByteBuf buffer = ByteBufUtil.writeUtf8(allocator, sequence);
        try {
            return buffer.getByte(buffer.writerIndex() - 1);
        } finally {
            buffer.release();
        }
    }

    @Benchmark
    public int writeUtf8AndAppend() {
        ByteBuf buffer = ByteBufUtil.writeUtf8(allocator, sequence);
        try {
            buffer.writeBytes(sequence.array(), sequence.arrayOffset(), sequence.length());
            buffer.writeBytes(sequence.array(), sequence.arrayOffset(), sequence.length());
            buffer.writeBytes(sequence.array(), sequence.arrayOffset(), sequence.length());
            return buffer.getByte(buffer.writerIndex() - 1);
        } finally {
            buffer.release();
        }
    }
}
