/*
 * Copyright 2018 The Netty Project
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
package io.netty.microbench.util;

import io.netty.util.Recycler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

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
        return super.newOptionsBuilder()
            .addProfiler("gc");
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
        final ArrayBlockingQueue<DummyObject> queue = new ArrayBlockingQueue(100);
    }

    // The allocation stats are the main thing interesting about this benchmark
    @Benchmark
    @Group("producerConsumer")
    public void producer(ProducerConsumerState state) throws Exception {
        state.queue.put(recycler.get());
    }

    @Benchmark
    @Group("producerConsumer")
    public void consumer(ProducerConsumerState state) throws Exception {
        state.queue.take().recycle();
    }

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

        public DummyObject() {
            this(null);
        }

        public DummyObject(Recycler.Handle<DummyObject> handle) {
            this.handle = handle;
        }

        public void recycle() {
            handle.recycle(this);
        }
    }
}
