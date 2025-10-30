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

import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.MiByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Threads(1)
@Fork(1)
public class ByteBufAllocatorRandomBenchmark extends AbstractMicrobenchmark {

    private static final PooledByteBufAllocator pooledAlloc = PooledByteBufAllocator.DEFAULT;
    private static final ByteBufAllocator adaptiveAllocator = new AdaptiveByteBufAllocator();
    private static final MiByteBufAllocator miMallocAllocator = new MiByteBufAllocator();

    private static final int SEED = 42;
    private SplittableRandom rand = new SplittableRandom(SEED);

    private static final int MAX_LIVE_BUFFERS = 8192;
    private final ByteBuf[] pooledDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private final ByteBuf[] adaptiveDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];
    private final ByteBuf[] mimallocDirectBuffers = new ByteBuf[MAX_LIVE_BUFFERS];

    private int[] releaseIndexes;
    private int nextReleaseIndex;

    private static final int MAX_SIZE = 1024 * 8; // 8 KiB

    // Use event-loop threads.
    public ByteBufAllocatorRandomBenchmark() {
        super(true, false);
    }

    @TearDown
    public void releaseBuffers() {
        List<ByteBuf[]> bufferLists = Arrays.asList(
                pooledDirectBuffers,
                adaptiveDirectBuffers,
                mimallocDirectBuffers);
        for (ByteBuf[] bufList : bufferLists) {
            for (ByteBuf buf : bufList) {
                if (buf != null && buf.refCnt() > 0) {
                    buf.release();
                }
            }
            Arrays.fill(bufList, null);
        }
    }

    @Setup
    public void setup() {
        releaseIndexes = new int[MAX_LIVE_BUFFERS];
        SplittableRandom rand = new SplittableRandom(SEED);
        // Pre-generate the to be released index.
        for (int i = 0; i < releaseIndexes.length; i++) {
            releaseIndexes[i] = rand.nextInt(releaseIndexes.length);
        }
    }

    private int getNextReleaseIndex() {
        int index = nextReleaseIndex;
        nextReleaseIndex = (nextReleaseIndex + 1) & (releaseIndexes.length - 1);
        return releaseIndexes[index];
    }

    private void directAlloc(Blackhole blackhole, ByteBufAllocator alloc, ByteBuf[] buffers) {
        int size = rand.nextInt(MAX_SIZE);
        int releaseIndex = getNextReleaseIndex();
        ByteBuf oldBuf = buffers[releaseIndex];
        if (oldBuf != null) {
            oldBuf.release();
        }
        ByteBuf newBuf = alloc.directBuffer(size);
        buffers[releaseIndex] = newBuf;
        blackhole.consume(buffers);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void pooledDirect(Blackhole blackhole) {
        directAlloc(blackhole, pooledAlloc, pooledDirectBuffers);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void adaptiveDirect(Blackhole blackhole) {
        directAlloc(blackhole, adaptiveAllocator, adaptiveDirectBuffers);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Benchmark
    public void mimallocDirect(Blackhole blackhole) {
        directAlloc(blackhole, miMallocAllocator, mimallocDirectBuffers);
    }
}
