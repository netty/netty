/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.microbenchmark.common;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;

public class MathOperationsBenchmark extends AbstractMicrobenchmark {
    private int index;
    private final int length = 1 << 20;
    private final int mask = length - 1;

    @Benchmark
    public int nextIndexNoConditionals() {
        return (index + 1) & mask;
    }

    @Benchmark
    public int nextIndexConditionals() {
        return index == length - 1 ? 0 : index + 1;
    }
}
