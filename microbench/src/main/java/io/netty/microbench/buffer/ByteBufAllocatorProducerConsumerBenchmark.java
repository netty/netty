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
import io.netty.buffer.MiByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;

import java.util.ArrayList;
import java.util.Queue;
import java.util.SplittableRandom;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
public class ByteBufAllocatorProducerConsumerBenchmark extends AbstractMicrobenchmark {

    private static final AdaptiveByteBufAllocator adaptiveAllocator = new AdaptiveByteBufAllocator();
    private static final MiByteBufAllocator miMallocAllocator = new MiByteBufAllocator();
    private static final PooledByteBufAllocator pooledAlloc = PooledByteBufAllocator.DEFAULT;

    // Use event-loop threads.
    public ByteBufAllocatorProducerConsumerBenchmark() {
        super(true, false);
    }

    // Allocation size list.
    private static final ArrayList<Integer> sizeList = new ArrayList<>();

    @State(Scope.Group)
    public static class ProducerConsumerState {
        private Queue<ByteBuf> queue;
        private Integer[] sizeIndexes;
        private int nextSizeIndex;

        @Setup
        public void init() {
            queue = PlatformDependent.newFixedMpmcQueue(1024);
            sizeIndexes = new Integer[MathUtil.findNextPositivePowerOfTwo(sizeList.size())];
            // Pre-generate the to be released index.
            SplittableRandom rand = new SplittableRandom(42);
            for (int i = 0; i < sizeIndexes.length; i++) {
                sizeIndexes[i] = rand.nextInt(sizeList.size());
            }
        }

        private int getNextSizeIndex() {
            int index = nextSizeIndex;
            nextSizeIndex = (nextSizeIndex + 1) & (sizeIndexes.length - 1);
            return index;
        }
    }

    @Benchmark
    @Group("pooled_direct")
    public void consumerPooledDirect(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("pooled_direct")
    public void producerPooledDirect(ProducerConsumerState state, Control control) {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(pooledAlloc.directBuffer(size))) {
                break;
            }
        }
    }

    @Benchmark
    @Group("adaptive_direct")
    public void consumerAdaptiveDirect(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("adaptive_direct")
    public void producerAdaptiveDirect(ProducerConsumerState state, Control control) {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(adaptiveAllocator.directBuffer(size))) {
                break;
            }
        }
    }

    @Benchmark
    @Group("mimalloc_direct")
    public void consumerMimallocDirect(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("mimalloc_direct")
    public void producerMimallocDirect(ProducerConsumerState state, Control control) {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(miMallocAllocator.directBuffer(size))) {
                break;
            }
        }
    }

    @Benchmark
    @Group("pooled_heap")
    public void consumerPooledHeap(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("pooled_heap")
    public void producerPooledHeap(ProducerConsumerState state, Control control) {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(pooledAlloc.heapBuffer(size))) {
                break;
            }
        }
    }

    @Benchmark
    @Group("adaptive_heap")
    public void consumerAdaptiveHeap(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("adaptive_heap")
    public void producerAdaptiveHeap(ProducerConsumerState state, Control control) throws Exception {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(adaptiveAllocator.heapBuffer(size))) {
                break;
            }
        }
    }

    @Benchmark
    @Group("mimalloc_heap")
    public void consumerMimallocHeap(ProducerConsumerState state, Control control, Blackhole blackhole) {
        Queue<ByteBuf> queue = state.queue;
        do {
            ByteBuf buf = queue.poll();
            if (buf != null) {
                blackhole.consume(buf.release());
                return;
            }
        } while (!control.stopMeasurement);
    }

    @Benchmark
    @Group("mimalloc_heap")
    public void producerMimallocHeap(ProducerConsumerState state, Control control) {
        Queue<ByteBuf> queue = state.queue;
        while (!control.stopMeasurement) {
            int sizeIndex = state.sizeIndexes[state.getNextSizeIndex()];
            int size = sizeList.get(sizeIndex);
            if (queue.offer(miMallocAllocator.heapBuffer(size))) {
                break;
            }
        }
    }

    /**
     * Copied from AllocationPatternSimulator.
     * An allocation pattern derived from a web socket proxy service.
     */
    private static final int[] WEB_SOCKET_PROXY_PATTERN = {
            // Size, Frequency
            9, 316,
            13, 3,
            15, 10344,
            17, 628,
            21, 316,
            36, 338,
            48, 338,
            64, 23,
            128, 17,
            256, 21272,
            287, 69,
            304, 65,
            331, 11,
            332, 7,
            335, 2,
            343, 2,
            362, 1,
            363, 16,
            365, 17,
            370, 11,
            371, 51,
            392, 11,
            393, 4,
            396, 3,
            401, 1,
            402, 3,
            413, 1,
            414, 2,
            419, 16,
            421, 1,
            423, 16,
            424, 46,
            433, 1,
            435, 1,
            439, 3,
            441, 13,
            444, 3,
            449, 1,
            450, 1,
            453, 2,
            455, 3,
            458, 3,
            462, 7,
            463, 8,
            464, 1,
            466, 59,
            470, 1,
            472, 2,
            475, 1,
            478, 2,
            480, 12,
            481, 16,
            482, 2,
            483, 2,
            486, 1,
            489, 2,
            493, 2,
            494, 1,
            495, 1,
            497, 14,
            498, 1,
            499, 2,
            500, 58,
            503, 1,
            507, 1,
            509, 2,
            510, 2,
            511, 13,
            512, 3,
            513, 4,
            516, 1,
            519, 2,
            520, 1,
            522, 5,
            523, 1,
            525, 15,
            526, 1,
            527, 55,
            528, 2,
            529, 1,
            530, 1,
            531, 3,
            533, 1,
            534, 1,
            535, 1,
            536, 10,
            538, 4,
            539, 3,
            540, 2,
            541, 1,
            542, 3,
            543, 10,
            545, 5,
            546, 1,
            547, 14,
            548, 1,
            549, 53,
            551, 1,
            552, 1,
            553, 1,
            554, 1,
            555, 2,
            556, 11,
            557, 3,
            558, 7,
            559, 4,
            561, 3,
            562, 1,
            563, 6,
            564, 3,
            565, 13,
            566, 31,
            567, 24,
            568, 1,
            569, 1,
            570, 4,
            571, 2,
            572, 9,
            573, 7,
            574, 3,
            575, 2,
            576, 4,
            577, 2,
            578, 7,
            579, 12,
            580, 38,
            581, 22,
            582, 1,
            583, 3,
            584, 5,
            585, 9,
            586, 9,
            587, 6,
            588, 3,
            589, 5,
            590, 8,
            591, 23,
            592, 42,
            593, 3,
            594, 5,
            595, 11,
            596, 10,
            597, 7,
            598, 5,
            599, 13,
            600, 26,
            601, 41,
            602, 8,
            603, 14,
            604, 18,
            605, 14,
            606, 16,
            607, 35,
            608, 57,
            609, 74,
            610, 13,
            611, 24,
            612, 22,
            613, 52,
            614, 88,
            615, 28,
            616, 23,
            617, 37,
            618, 70,
            619, 74,
            620, 31,
            621, 59,
            622, 110,
            623, 37,
            624, 67,
            625, 110,
            626, 55,
            627, 140,
            628, 71,
            629, 141,
            630, 141,
            631, 147,
            632, 190,
            633, 254,
            634, 349,
            635, 635,
            636, 5443,
            637, 459,
            639, 1,
            640, 2,
            642, 1,
            644, 2,
            645, 1,
            647, 1,
            649, 1,
            650, 1,
            652, 1,
            655, 3,
            656, 1,
            658, 4,
            659, 2,
            660, 1,
            661, 1,
            662, 6,
            663, 8,
            664, 9,
            665, 4,
            666, 5,
            667, 62,
            668, 5,
            693, 1,
            701, 2,
            783, 1,
            941, 1,
            949, 1,
            958, 16,
            988, 1,
            1024, 29289,
            1028, 1,
            1086, 1,
            1249, 2,
            1263, 1,
            1279, 24,
            1280, 11,
            1309, 1,
            1310, 1,
            1311, 2,
            1343, 1,
            1360, 2,
            1483, 1,
            1567, 1,
            1957, 1,
            2048, 2636,
            2060, 1,
            2146, 1,
            2190, 1,
            2247, 1,
            2273, 1,
            2274, 1,
            2303, 106,
            2304, 45,
            2320, 1,
            2333, 10,
            2334, 14,
            2335, 7,
            2367, 7,
            2368, 2,
            2384, 7,
            2399, 1,
            2400, 14,
            2401, 6,
            2423, 1,
            2443, 9,
            2444, 1,
            2507, 3,
            3039, 1,
            3140, 1,
            3891, 1,
            3893, 1,
            4096, 26,
            4118, 1,
            4321, 1,
            4351, 226,
            4352, 15,
            4370, 1,
            4381, 1,
            4382, 11,
            4383, 10,
            4415, 4,
            4416, 3,
            4432, 5,
            4447, 1,
            4448, 31,
            4449, 14,
            4471, 1,
            4491, 42,
            4492, 16,
            4555, 26,
            4556, 19,
            4571, 1,
            4572, 2,
            4573, 53,
            4574, 165,
            5770, 1,
            5803, 2,
            6026, 1,
            6144, 2,
            6249, 1,
            6278, 1,
            6466, 1,
            6680, 1,
            6726, 2,
            6728, 1,
            6745, 1,
            6746, 1,
            6759, 1,
            6935, 1,
            6978, 1,
            6981, 2,
            6982, 1,
            7032, 1,
            7081, 1,
            7086, 1,
            7110, 1,
            7172, 3,
            7204, 2,
            7236, 2,
            7238, 1,
            7330, 1,
            7427, 3,
            7428, 1,
            7458, 1,
            7459, 1,
            7650, 2,
            7682, 6,
            7765, 1,
            7937, 3,
            7969, 1,
            8192, 2,
            8415, 1,
            8447, 555,
            8478, 3,
            8479, 5,
            8511, 2,
            8512, 1,
            8528, 1,
            8543, 2,
            8544, 9,
            8545, 8,
            8567, 1,
            8587, 16,
            8588, 12,
            8650, 1,
            8651, 9,
            8652, 9,
            8668, 3,
            8669, 46,
            8670, 195,
            8671, 6,
            10240, 4,
            14336, 1,
            14440, 4,
            14663, 3,
            14919, 1,
            14950, 2,
            15002, 1,
            15159, 1,
            15173, 2,
            15205, 1,
            15395, 1,
            15396, 1,
            15397, 2,
            15428, 1,
            15446, 1,
            15619, 7,
            15651, 5,
            15683, 2,
            15874, 8,
            15906, 8,
            15907, 2,
            16128, 2,
            16129, 37,
            16161, 3,
            16352, 2,
            16383, 1,
            16384, 42,
            16610, 2,
            16639, 9269,
            16704, 2,
            16736, 3,
            16737, 2,
            16779, 2,
            16780, 7,
            16843, 2,
            16844, 5,
            16860, 6,
            16861, 67,
            16862, 281,
            16863, 13,
            18432, 6,
    };

    static {
        // Flat the array to list.
        for (int i = 0; i < WEB_SOCKET_PROXY_PATTERN.length; i += 2) {
            int size = WEB_SOCKET_PROXY_PATTERN[i];
            int frequency = WEB_SOCKET_PROXY_PATTERN[i + 1];
            for (int j = 0; j < frequency; j++) {
                sizeList.add(size);
            }
        }
    }
}
