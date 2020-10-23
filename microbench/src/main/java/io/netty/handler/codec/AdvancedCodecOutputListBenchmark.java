/*
 * Copyright 2017 The Netty Project
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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

@State(Scope.Benchmark)
@Threads(16)
public class AdvancedCodecOutputListBenchmark extends AbstractMicrobenchmark {

    private static final Object ELEMENT = new Object();

    @Param({ "1", "4" })
    public int elements;

    @Benchmark
    public boolean codecOutListAllocRecycle() {
        return benchmark(elements, CodecOutputList.newInstance(), CodecOutputList.newInstance(),
                CodecOutputList.newInstance(), CodecOutputList.newInstance());
    }

    private static boolean benchmark(int elements, CodecOutputList list1, CodecOutputList list2,
                                     CodecOutputList list3, CodecOutputList list4) {
        return (benchmark(elements, list1) == benchmark(elements, list2)) ==
                (benchmark(elements, list3) == benchmark(elements, list4));
    }

    private static boolean benchmark(int elements, CodecOutputList list) {
        for (int i = 0; i < elements; ++i) {
            list.add(ELEMENT);
        }
        list.recycle();
        return list.insertSinceRecycled();
    }
}
