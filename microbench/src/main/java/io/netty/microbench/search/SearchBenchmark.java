/*
 * Copyright 2020 The Netty Project
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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.search.MultiSearchProcessorFactory;
import io.netty.buffer.search.SearchProcessorFactory;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
public class SearchBenchmark extends AbstractMicrobenchmark {

    private static final long SEED = 123;

    public enum ByteBufType {
        HEAP {
            @Override
            ByteBuf newBuffer(byte[] bytes) {
                return Unpooled.wrappedBuffer(bytes, 0, bytes.length);
            }
        },
        COMPOSITE {
            @Override
            ByteBuf newBuffer(byte[] bytes) {
                CompositeByteBuf buf = Unpooled.compositeBuffer();
                int length = bytes.length;
                int offset = 0;
                int capacity = length / 8; // 8 buffers per composite

                while (length > 0) {
                    buf.addComponent(true, Unpooled.wrappedBuffer(bytes, offset, Math.min(length, capacity)));
                    length -= capacity;
                    offset += capacity;
                }
                return buf;
            }
        },
        DIRECT {
            @Override
            ByteBuf newBuffer(byte[] bytes) {
                return Unpooled.directBuffer(bytes.length).writeBytes(bytes);
            }
        };
        abstract ByteBuf newBuffer(byte[] bytes);
    }

    public enum Input {
        RANDOM_256B {
            @Override
            byte[] getNeedle() {
                return new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' };
            }
            @Override
            byte[] getHaystack() {
                return randomBytes(256);
            }
        },
        RANDOM_2KB {
            @Override
            byte[] getNeedle() {
                return new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h' };
            }
            @Override
            byte[] getHaystack() {
                return randomBytes(2048);
            }
        },
        WORST_CASE {
            @Override
            byte[] getNeedle() {
                byte[] needle = new byte[64];
                Arrays.fill(needle, (byte) 'a');
                needle[needle.length - 1] = 'b';
                return needle;
            }
            @Override
            byte[] getHaystack() {
                byte[] haystack = new byte[256];
                Arrays.fill(haystack, (byte) 'a');
                return haystack;
            }
        };

        abstract byte[] getNeedle();
        abstract byte[] getHaystack();

        private static byte[] randomBytes(int size) {
            byte[] bytes = new byte[size];
            Random rnd = new Random(SEED);
            for (int i = 0; i < size; i++) {
                bytes[i] = (byte) (' ' + rnd.nextInt(128 - ' '));
            }
            return bytes;
        }
    }

    @Param
    public Input input;

    @Param
    public ByteBufType bufferType;

    private ByteBuf needle, haystack;
    private byte[] needleBytes, haystackBytes;
    private SearchProcessorFactory kmpFactory, shiftingBitMaskFactory, ahoCorasicFactory;

    @Setup
    public void setup() {
        needleBytes = input.getNeedle();
        haystackBytes = input.getHaystack();

        needle = Unpooled.wrappedBuffer(needleBytes);
        haystack = bufferType.newBuffer(haystackBytes);

        kmpFactory = SearchProcessorFactory.newKmpSearchProcessorFactory(needleBytes);
        shiftingBitMaskFactory = SearchProcessorFactory.newShiftingBitMaskSearchProcessorFactory(needleBytes);
        ahoCorasicFactory = MultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(needleBytes);
    }

    @TearDown
    public void teardown() {
        needle.release();
        haystack.release();
    }

    @Benchmark
    public int indexOf() {
        return ByteBufUtil.indexOf(needle, haystack);
    }

    @Benchmark
    public int kmp() {
        return haystack.forEachByte(kmpFactory.newSearchProcessor());
    }

    @Benchmark
    public int shiftingBitMask() {
        return haystack.forEachByte(shiftingBitMaskFactory.newSearchProcessor());
    }

    @Benchmark
    public int ahoCorasic() {
        return haystack.forEachByte(ahoCorasicFactory.newSearchProcessor());
    }

}
