/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.RecyclableArrayList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Benchmark)
public class CodecOutputListBenchmark extends AbstractMicrobenchmark {

    private static final Object ELEMENT = new Object();
    private CodecOutputList codecOutputList;
    private RecyclableArrayList recycleableArrayList;
    private List<Object> arrayList;

    @Param({ "1", "4" })
    public int elements;

    @TearDown
    public void destroy() {
        codecOutputList.recycle();
        recycleableArrayList.recycle();
    }

    @Benchmark
    public void codecOutList() {
        codecOutputList = CodecOutputList.newInstance();
        benchmarkAddAndClear(codecOutputList, elements);
    }

    @Benchmark
    public void recyclableArrayList() {
        recycleableArrayList = RecyclableArrayList.newInstance(16);
        benchmarkAddAndClear(recycleableArrayList, elements);
    }

    @Benchmark
    public void arrayList() {
        arrayList = new ArrayList<Object>(16);
        benchmarkAddAndClear(arrayList, elements);
    }

    private static void benchmarkAddAndClear(List<Object> list, int elements) {
        for (int i = 0; i < elements; i++) {
            list.add(ELEMENT);
        }
        list.clear();
    }
}
