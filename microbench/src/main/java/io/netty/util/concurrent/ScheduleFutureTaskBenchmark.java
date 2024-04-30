/*
 * Copyright 2019 The Netty Project
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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import io.netty.microbench.util.AbstractMicrobenchmark;

@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class ScheduleFutureTaskBenchmark extends AbstractMicrobenchmark {

    static final Callable<Void> NO_OP = new Callable<Void>() {
        @Override
        public Void call() throws Exception {
            return null;
        }
    };

    @State(Scope.Thread)
    public static class ThreadState {

        @Param({ "100000" })
        int num;

        AbstractScheduledEventExecutor eventLoop;

        @Setup(Level.Trial)
        public void reset() {
            eventLoop = (AbstractScheduledEventExecutor) new MultiThreadIoEventLoopGroup(
                    1, NioIoHandler.newFactory()).next();
        }

        @Setup(Level.Invocation)
        public void clear() {
            eventLoop.submit(new Runnable() {
                @Override
                public void run() {
                    eventLoop.cancelScheduledTasks();
                }
            }).awaitUninterruptibly();
        }

        @TearDown(Level.Trial)
        public void shutdown() {
            clear();
            eventLoop.parent().shutdownGracefully().awaitUninterruptibly();
        }
    }

    @Benchmark
    @Threads(3)
    public Future<?> scheduleLots(final ThreadState threadState) {
        return threadState.eventLoop.submit(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= threadState.num; i++) {
                    threadState.eventLoop.schedule(NO_OP, i, TimeUnit.HOURS);
                }
            }
        }).syncUninterruptibly();
    }

    @Benchmark
    @Threads(1)
    public Future<?> scheduleLotsOutsideLoop(final ThreadState threadState) {
        final AbstractScheduledEventExecutor eventLoop = threadState.eventLoop;
        for (int i = 1; i <= threadState.num; i++) {
            eventLoop.schedule(NO_OP, i, TimeUnit.HOURS);
        }
        return null;
    }

    @Benchmark
    @Threads(1)
    public Future<?> scheduleCancelLotsOutsideLoop(final ThreadState threadState) {
        final AbstractScheduledEventExecutor eventLoop = threadState.eventLoop;
        for (int i = 1; i <= threadState.num; i++) {
            eventLoop.schedule(NO_OP, i, TimeUnit.HOURS).cancel(false);
        }
        return null;
    }
}
