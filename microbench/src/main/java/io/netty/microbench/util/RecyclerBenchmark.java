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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = AbstractMicrobenchmarkBase.DEFAULT_WARMUP_ITERATIONS, time = 1)
@Measurement(iterations = AbstractMicrobenchmarkBase.DEFAULT_MEASURE_ITERATIONS, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RecyclerBenchmark extends AbstractMicrobenchmark {
    private Recycler<DummyObject> recycler = new Recycler<DummyObject>() {
        @Override
        protected DummyObject newObject(Recycler.Handle<DummyObject> handle) {
            return new DummyObject(handle);
        }
    };

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler("gc");
    }

    @Benchmark
    public DummyObject plainNew() {
        return new DummyObject();
    }

    @Benchmark
    public DummyObject recyclerGetAndOrphan() {
        return recycler.get();
    }

    @Benchmark
    public DummyObject recyclerGetAndRecycle() {
        DummyObject o = recycler.get();
        o.recycle();
        return o;
    }

    @State(Scope.Benchmark)
    public static class ProducerConsumerState {
        final ArrayBlockingQueue<DummyObject> queue = new ArrayBlockingQueue<DummyObject>(100);
    }

    // The allocation stats are the main thing interesting about this benchmark
    @Benchmark
    @Group("producerConsumer")
    public void producer(ProducerConsumerState state, Control control) throws Exception {
        ArrayBlockingQueue<DummyObject> queue = state.queue;
        DummyObject object = recycler.get();
        while (!control.stopMeasurement) {
            if (queue.offer(object)) {
                break;
            }
        }
    }

    @Benchmark
    @Group("producerConsumer")
    public void consumer(ProducerConsumerState state, Control control) throws Exception {
        DummyObject object;
        do {
            object = state.queue.poll();
            if (object != null) {
                object.recycle();
                return;
            }
        } while (!control.stopMeasurement);
    }

    @SuppressWarnings("unused")
    private static final class DummyObject {
        private final Recycler.Handle<DummyObject> handle;
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

        DummyObject(Recycler.Handle<DummyObject> handle) {
            this.handle = handle;
        }

        public void recycle() {
            handle.recycle(this);
        }
    }
}
