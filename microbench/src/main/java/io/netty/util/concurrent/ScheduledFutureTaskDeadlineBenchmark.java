/*
 * Copyright 2022 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ScheduledFutureTaskDeadlineBenchmark extends AbstractMicrobenchmark {
    @State(Scope.Thread)
    public static class ThreadState {

        AbstractScheduledEventExecutor eventLoop;
        ScheduledFutureTask<?> future;

        @Setup(Level.Trial)
        public void reset() {
            eventLoop = (AbstractScheduledEventExecutor) new MultiThreadIoEventLoopGroup(
                    1, NioIoHandler.newFactory()).next();
            future = (ScheduledFutureTask<?>) eventLoop.schedule(new Runnable() {
                @Override
                public void run() {
                }
            }, 100, TimeUnit.DAYS);
        }

        @TearDown(Level.Trial)
        public void shutdown() {
            future.cancel(true);
            eventLoop.parent().shutdownGracefully().awaitUninterruptibly();
        }
    }

    @Benchmark
    @Threads(1)
    public long requestDeadline(final ThreadState threadState) {
        return threadState.future.delayNanos();
    }
}
