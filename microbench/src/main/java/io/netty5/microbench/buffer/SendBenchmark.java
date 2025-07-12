/*
 * Copyright 2020 The Netty Project
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
package io.netty5.microbench.buffer;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.util.Send;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;

@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 5, jvmArgsAppend = { "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints" })
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SendBenchmark {
    private static final BufferAllocator NON_POOLED = BufferAllocator.onHeapUnpooled();
    private static final BufferAllocator POOLED = BufferAllocator.onHeapPooled();
    private static final Function<Send<Buffer>, Send<Buffer>> BUFFER_BOUNCE = send -> {
        try (Buffer buf = send.receive()) {
            return buf.send();
        }
    };

    @Benchmark
    public Buffer sendNonPooled() throws Exception {
        try (Buffer buf = NON_POOLED.allocate(8)) {
            try (Buffer receive = completedFuture(buf.send()).thenApplyAsync(BUFFER_BOUNCE).get().receive()) {
                return receive;
            }
        }
    }

    @Benchmark
    public Buffer sendPooled() throws Exception {
        try (Buffer buf = POOLED.allocate(8)) {
            try (Buffer receive = completedFuture(buf.send()).thenApplyAsync(BUFFER_BOUNCE).get().receive()) {
                return receive;
            }
        }
    }
}
