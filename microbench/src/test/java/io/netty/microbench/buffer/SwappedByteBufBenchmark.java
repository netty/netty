/*
* Copyright 2014 The Netty Project
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
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.ByteOrder;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 25)
public class SwappedByteBufBenchmark extends AbstractMicrobenchmark {
    private ByteBuf swappedByteBuf;
    private ByteBuf unsafeSwappedByteBuf;

    @Setup
    public void setup() {
        swappedByteBuf = new SwappedByteBuf(Unpooled.directBuffer(8));
        unsafeSwappedByteBuf = Unpooled.directBuffer(8).order(ByteOrder.LITTLE_ENDIAN);
        if (unsafeSwappedByteBuf.getClass().equals(SwappedByteBuf.class)) {
            throw new IllegalStateException("Should not use " + SwappedByteBuf.class.getSimpleName());
        }
    }

    @Param("16384")
    public int size;

    @Benchmark
    public void swappedByteBufSetInt() {
        swappedByteBuf.setLong(0, size);
    }

    @Benchmark
    public void swappedByteBufSetShort() {
        swappedByteBuf.setShort(0, size);
    }

    @Benchmark
    public void swappedByteBufSetLong() {
        swappedByteBuf.setLong(0, size);
    }

    @Benchmark
    public void unsafeSwappedByteBufSetInt() {
        unsafeSwappedByteBuf.setInt(0, size);
    }

    @Benchmark
    public void unsafeSwappedByteBufSetShort() {
        unsafeSwappedByteBuf.setShort(0, size);
    }

    @Benchmark
    public void unsafeSwappedByteBufSetLong() {
        unsafeSwappedByteBuf.setLong(0, size);
    }
}
