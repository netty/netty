/*
 * Copyright 2021 The Netty Project
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
package io.netty.microbench.util;

import io.netty.util.Recycler;
import io.netty.util.Recycler.EnhancedHandle;
import io.netty.util.internal.PlatformDependent;
import org.jctools.queues.MpmcArrayQueue;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = AbstractMicrobenchmarkBase.DEFAULT_WARMUP_ITERATIONS, time = 1)
@Measurement(iterations = AbstractMicrobenchmarkBase.DEFAULT_MEASURE_ITERATIONS, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RecyclerBenchmark extends AbstractMicrobenchmark {

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler("gc");
    }

    @Benchmark
    public DummyObject plainNew() {
        return new DummyObject();
    }

    @Benchmark
    public DummyObject recyclerGetAndOrphan(ProducerConsumerState state) {
        return state.recycler.get();
    }

    @Benchmark
    public DummyObject recyclerGetAndRecycle(ProducerConsumerState state) {
        DummyObject o = state.recycler.get();
        o.recycle();
        return o;
    }

    @Benchmark
    public DummyObject recyclerGetAndUnguardedRecycle(ProducerConsumerState state) {
        DummyObject o = state.recycler.get();
        o.unguardedRecycle();
        return o;
    }

    @State(Scope.Benchmark)
    public static class ProducerConsumerState {
        Queue<DummyObject> queue;

        Recycler<DummyObject> recycler;

        @Setup
        public void init() {
            queue = PlatformDependent.hasUnsafe()? new MpmcArrayQueue<DummyObject>(100) :
                    new MpmcAtomicArrayQueue<DummyObject>(100);
            recycler = new Recycler<DummyObject>() {
                @Override
                protected DummyObject newObject(Recycler.Handle<DummyObject> handle) {
                    return new DummyObject((EnhancedHandle<DummyObject>) handle);
                }
            };
        }
    }

    // The allocation stats are the main thing interesting about this benchmark
    @Benchmark
    @Group("producerConsumer")
    public void producer(ProducerConsumerState state, Control control) throws Exception {
        Queue<DummyObject> queue = state.queue;
        DummyObject object = state.recycler.get();
        while (!control.stopMeasurement) {
            if (queue.offer(object)) {
                break;
            }
        }
    }

    @Benchmark
    @Group("producerConsumer")
    public void consumer(ProducerConsumerState state, Control control) throws Exception {
        Queue<DummyObject> queue = state.queue;
        DummyObject object;
        do {
            object = queue.poll();
            if (object != null) {
                object.recycle();
                return;
            }
        } while (!control.stopMeasurement);
    }

    // The allocation stats are the main thing interesting about this benchmark
    @Benchmark
    @Group("unguardedProducerConsumer")
    public void unguardedProducer(ProducerConsumerState state, Control control) throws Exception {
        Queue<DummyObject> queue = state.queue;
        DummyObject object = state.recycler.get();
        while (!control.stopMeasurement) {
            if (queue.offer(object)) {
                break;
            }
        }
    }

    @Benchmark
    @Group("unguardedProducerConsumer")
    public void unguardedConsumer(ProducerConsumerState state, Control control) throws Exception {
        Queue<DummyObject> queue = state.queue;
        DummyObject object;
        do {
            object = queue.poll();
            if (object != null) {
                object.unguardedRecycle();
                return;
            }
        } while (!control.stopMeasurement);
    }

    @SuppressWarnings("unused")
    private static final class DummyObject {
        private final EnhancedHandle<DummyObject> handle;
        private long l1;
        private long l2;
        private long l3;
        private long l4;
        private long l5;
        private Object o1;
        private Object o2;
        private Object o3;
        private Object o4;
        private Object o5;

        DummyObject() {
            this(null);
        }

        DummyObject(EnhancedHandle<DummyObject> handle) {
            this.handle = handle;
        }

        public void recycle() {
            handle.recycle(this);
        }

        public void unguardedRecycle() {
            handle.unguardedRecycle(this);
        }
    }
}
