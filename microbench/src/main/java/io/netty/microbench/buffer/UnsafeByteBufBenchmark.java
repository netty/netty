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
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledInternalUnsafeDirectByteBuf;
import io.netty.buffer.UnpooledUnsafeDirectByteBuf;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;


public class UnsafeByteBufBenchmark extends AbstractMicrobenchmark {

    private ByteBuf internalUnsafeBuffer;
    private ByteBuf unsafeBuffer;
    private ByteBuf buffer;
    private ByteBuffer byteBuffer;

    @Setup
    public void setup() {
        buffer = new UnpooledDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, 64, 64);
        internalUnsafeBuffer = new UnpooledInternalUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, 64, 64);
        unsafeBuffer = new UnpooledUnsafeDirectByteBuf(UnpooledByteBufAllocator.DEFAULT, 64, 64);
        byteBuffer = ByteBuffer.allocateDirect(64);
        buffer.writeLong(1);
        unsafeBuffer.writeLong(1);
        internalUnsafeBuffer.writeLong(1);
        byteBuffer.putLong(1);
    }

    @TearDown
    public void tearDown() {
        buffer.release();
        unsafeBuffer.release();
        internalUnsafeBuffer.release();
    }

    @Override
    protected String[] jvmArgs() {
        // Ensure we minimize the GC overhead for this benchmark and also open up required package.
        // See also https://shipilev.net/jvm-anatomy-park/7-initialization-costs/
        return new String[] { "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED" };
    }
    @Benchmark
    public long getLongByteBuf() {
        return buffer.getLong(0);
    }

    @Benchmark
    public long getLongUnsafeByteBuf() {
        return unsafeBuffer.getLong(0);
    }

    @Benchmark
    public long getLongInternalUnsafeByteBuf() {
        return internalUnsafeBuffer.getLong(0);
    }

    @Benchmark
    public long getLong() {
        return byteBuffer.getLong(0);
    }
}
