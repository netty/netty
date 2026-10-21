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

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Copies into a direct buffer of the {@link AdaptiveByteBufAllocator} from sources of different kinds, as
 * {@code target.writeBytes(source)} does. Run with {@code -prof gc} to see the allocations per copy.
 *
 * <p>Unsafe is enabled explicitly, since Netty disables it by default on Java 25+; to compare without it, pass
 * {@code -jvmArgsAppend -Dio.netty.noUnsafe=true}.
 */
@Fork(value = 2, jvmArgsAppend = "-Dio.netty.noUnsafe=false")
public class AdaptiveByteBufSetBytesBenchmark extends AbstractMicrobenchmark {

    public enum Source {
        ADAPTIVE,
        ADAPTIVE_SLICE,
        POOLED_DIRECT,
        POOLED_DIRECT_SLICE,
        UNPOOLED_HEAP,
        COMPOSITE
    }

    @Param({
            "64",
            "1024",
            "16384",
    })
    private int size;

    @Param
    private Source source;

    private final AdaptiveByteBufAllocator adaptiveAllocator = new AdaptiveByteBufAllocator();
    private ByteBuf sourceRoot;
    private ByteBuf src;
    private ByteBuf target;

    @Setup
    public void setup() {
        switch (source) {
            case ADAPTIVE:
                sourceRoot = adaptiveAllocator.directBuffer(size, size);
                src = sourceRoot;
                break;
            case ADAPTIVE_SLICE:
                sourceRoot = adaptiveAllocator.directBuffer(size + 8, size + 8);
                src = sourceRoot.slice(8, size);
                break;
            case POOLED_DIRECT:
                sourceRoot = PooledByteBufAllocator.DEFAULT.directBuffer(size, size);
                src = sourceRoot;
                break;
            case POOLED_DIRECT_SLICE:
                sourceRoot = PooledByteBufAllocator.DEFAULT.directBuffer(size + 8, size + 8);
                src = sourceRoot.slice(8, size);
                break;
            case UNPOOLED_HEAP:
                sourceRoot = Unpooled.buffer(size, size);
                src = sourceRoot;
                break;
            case COMPOSITE:
                CompositeByteBuf composite = Unpooled.compositeBuffer();
                composite.addComponents(true,
                        PooledByteBufAllocator.DEFAULT.directBuffer(size / 2).writeZero(size / 2),
                        PooledByteBufAllocator.DEFAULT.directBuffer(size - size / 2).writeZero(size - size / 2));
                sourceRoot = composite;
                src = composite;
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(source));
        }
        for (int i = 0; i < size; i++) {
            src.setByte(i, 0xA5);
        }
        target = adaptiveAllocator.directBuffer(size, size);
    }

    @Benchmark
    public ByteBuf setBytes() {
        return target.setBytes(0, src, 0, size);
    }

    @TearDown
    public void tearDown() {
        target.release();
        sourceRoot.release();
    }
}
