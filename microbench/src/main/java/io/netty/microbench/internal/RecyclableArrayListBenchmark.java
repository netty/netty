/*
 * Copyright 2012 The Netty Project
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
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.RecyclableArrayList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

/**
 * This class benchmarks different allocators with different allocation sizes.
 */
@State(Scope.Benchmark)
@Threads(4)
@Measurement(iterations = 10, batchSize = 100)
public class RecyclableArrayListBenchmark extends AbstractMicrobenchmark {

    @Param({ "00000", "00256", "01024", "04096", "16384", "65536" })
    public int size;

    @Benchmark
    public boolean recycleSameThread() {
        RecyclableArrayList list = RecyclableArrayList.newInstance(size);
        return list.recycle();
    }
}
