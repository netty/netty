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
package io.netty.microbench.concurrent;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class ScheduledFutureTaskBenchmark extends AbstractMicrobenchmark {

    static final EventLoop executor = new DefaultEventLoop();

    @State(Scope.Thread)
    public static class FuturesHolder {

        private static final Callable<Void> NO_OP = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                return null;
            }
        };

        @Param({ "100", "1000", "10000", "100000" })
        int num;

        final List<ScheduledFuture<Void>> futures = new ArrayList<ScheduledFuture<Void>>();

        @Setup(Level.Invocation)
        public void reset() {
            futures.clear();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    for (int i = 1; i <= num; i++) {
                        futures.add(executor.schedule(NO_OP, i, TimeUnit.HOURS));
                    }
                }
            }).syncUninterruptibly();
        }
    }

    @TearDown(Level.Trial)
    public void stop() throws Exception {
        executor.shutdownGracefully().syncUninterruptibly();
    }

    @Benchmark
    public Future<?> cancelInOrder(final FuturesHolder futuresHolder) {
        return executor.submit(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < futuresHolder.num; i++) {
                    futuresHolder.futures.get(i).cancel(false);
                }
            }
        }).syncUninterruptibly();
    }

    @Benchmark
    public Future<?> cancelInReverseOrder(final FuturesHolder futuresHolder) {
        return executor.submit(new Runnable() {
            @Override
            public void run() {
                for (int i = futuresHolder.num - 1; i >= 0; i--) {
                    futuresHolder.futures.get(i).cancel(false);
                }
            }
        }).syncUninterruptibly();
    }
}
