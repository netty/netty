/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.concurrent;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class DefaultPromiseBenchmark extends AbstractMicrobenchmark {
    private ExecutorService executorService;
    private EventExecutor executor;
    private TestGenericFutureListener[] listeners;
    private TestGenericFutureListener[] lastListeners;
    private static CountDownLatch testLatch;
    private final Runnable toEventLoopRunnable = new Runnable() {
        @Override
        public void run() {
            startOutEventLoop();
        }
    };

    @Param({ "1", "10" })
    public int numPromises;

    @Param({ "1", "4" })
    public int numListeners;

    @Param({ "0", "4" })
    public int lateListeners;

    @Setup(Level.Trial)
    public void setupTrial() {
        executorService = Executors.newSingleThreadExecutor();
        executor = new DefaultEventExecutor(executorService);
        listeners = new TestGenericFutureListener[numListeners];
        for (int i = 0; i < listeners.length; ++i) {
            listeners[i] = new TestGenericFutureListener();
        }
        lastListeners = new TestGenericFutureListener[lateListeners];
        for (int i = 0; i < lastListeners.length; ++i) {
            lastListeners[i] = new TestGenericFutureListener();
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        testLatch = new CountDownLatch(numPromises * (listeners.length + lastListeners.length));
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        final Future<?> f;
        try {
            executorService.shutdown();
        } finally {
            f = executor.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            f.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    public void testInEventLoop() throws Exception {
        startInEventLoop();
        testLatch.await();
    }

    @Benchmark
    public void testOutEventLoop() throws Exception {
        startOutEventLoop();
        testLatch.await();
    }

    private void startInEventLoop() {
        executor.execute(toEventLoopRunnable);
    }

    private void startOutEventLoop() {
        for (int i = 0; i < numPromises; ++i) {
            Promise<Void> p = new DefaultPromise<Void>(executor);
            p.addListeners(listeners);
            p.setSuccess(null);
            p.addListeners(lastListeners);
        }
    }

    private static final class TestGenericFutureListener implements GenericFutureListener<Future<Void>> {
        @Override
        public void operationComplete(Future<Void> future) throws Exception {
            testLatch.countDown();
        }
    }
}
