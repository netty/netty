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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;


@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 25)
public class SlicedByteBufBenchmark extends AbstractMicrobenchmark {

    private ByteBuf slicedByteBuf;
    private ByteBuf slicedAbstractByteBuf;
    private String ascii;

    @Setup
    public void setup() {
        // Use buffer sizes that will also allow to write UTF-8 without grow the buffer
        ByteBuf buffer = Unpooled.buffer(512).retain();
        slicedByteBuf = buffer.slice(0, 256);
        slicedAbstractByteBuf = buffer.slice(0, 256);

        if (slicedByteBuf.getClass() == slicedAbstractByteBuf.getClass()) {
            throw new IllegalStateException();
        }

        StringBuilder asciiSequence = new StringBuilder(128);
        for (int i = 0; i < 128; i++) {
            asciiSequence.append('a');
        }
        ascii = asciiSequence.toString();
    }

    @TearDown
    public void tearDown() {
        slicedByteBuf.release();
        slicedAbstractByteBuf.release();
    }

    @Benchmark
    public void writeAsciiStringSlice() {
        slicedByteBuf.resetWriterIndex();
        ByteBufUtil.writeAscii(slicedByteBuf, ascii);
    }

    @Benchmark
    public void writeAsciiStringSliceAbstract() {
        slicedAbstractByteBuf.resetWriterIndex();
        ByteBufUtil.writeAscii(slicedAbstractByteBuf, ascii);
    }
}
