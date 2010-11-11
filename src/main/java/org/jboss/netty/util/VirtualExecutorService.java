/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;


/**
 * A delegating {@link ExecutorService} with its own termination management.
 * <p>
 * {@link VirtualExecutorService} is used when you want to inject an
 * {@link ExecutorService} but you do not want to allow the explicit termination
 * of threads on shutdown request.  It is particularly useful when the
 * {@link ExecutorService} to inject is shared by different components and
 * the life cycle of the components depend on the termination of the injected
 * {@link ExecutorService}.
 *
 * <pre>
 * ExecutorService globalExecutor = ...;
 * ExecutorService virtualExecutor = new {@link VirtualExecutorService}(globalExecutor);
 *
 * {@link ChannelFactory} factory =
 *         new {@link NioServerSocketChannelFactory}(virtualExecutor, virtualExecutor);
 * ...
 *
 * // ChannelFactory.releaseExternalResources() shuts down the executor and
 * // interrupts the I/O threads to terminate all I/O tasks and to release all
 * // resources acquired by ChannelFactory.
 * factory.releaseExternalResources();
 *
 * // Note that globalExecutor is not shut down because VirtualExecutorService
 * // implements its own termination management. All threads which were acquired
 * // by ChannelFactory via VirtualExecutorService are returned to the pool.
 * assert !globalExecutor.isShutdown();
 * </pre>
 *
 * <h3>The differences from an ordinary {@link ExecutorService}</h3>
 *
 * A shutdown request ({@link #shutdown()} or {@link #shutdownNow()}) does not
 * shut down its parent {@link Executor} but simply sets its internal flag to
 * reject further execution request.
 * <p>
 * {@link #shutdownNow()} interrupts only the thread which is executing the
 * task executed via {@link VirtualExecutorService}.
 * <p>
 * {@link #awaitTermination(long, TimeUnit)} does not wait for real thread
 * termination but wait until {@link VirtualExecutorService} is shut down and
 * its active tasks are finished and the threads are returned to the parent
 * {@link Executor}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2122 $, $Date: 2010-02-02 11:00:04 +0900 (Tue, 02 Feb 2010) $
 *
 * @apiviz.landmark
 */
public class VirtualExecutorService extends AbstractExecutorService {

    private final Executor e;
    private final ExecutorService s;
    final Object startStopLock = new Object();
    volatile boolean shutdown;
    Set<Thread> activeThreads = new MapBackedSet<Thread>(new IdentityHashMap<Thread, Boolean>());

    /**
     * Creates a new instance with the specified parent {@link Executor}.
     */
    public VirtualExecutorService(Executor parent) {
        if (parent == null) {
            throw new NullPointerException("parent");
        }

        if (parent instanceof ExecutorService) {
            e = null;
            s = (ExecutorService) parent;
        } else {
            e = parent;
            s = null;
        }
    }

    public boolean isShutdown() {
        synchronized (startStopLock) {
            return shutdown;
        }
    }

    public boolean isTerminated() {
        synchronized (startStopLock) {
            return shutdown && activeThreads.isEmpty();
        }
    }

    public void shutdown() {
        synchronized (startStopLock) {
            if (shutdown) {
                return;
            }
            shutdown = true;
        }
    }

    public List<Runnable> shutdownNow() {
        synchronized (startStopLock) {
            if (!isTerminated()) {
                shutdown();
                for (Thread t: activeThreads) {
                    t.interrupt();
                }
            }
        }

        return Collections.emptyList();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        synchronized (startStopLock) {
            while (!isTerminated()) {
                startStopLock.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
            }

            return isTerminated();
        }
    }

    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }

        if (shutdown) {
            throw new RejectedExecutionException();
        }

        if (s != null) {
            s.execute(new ChildExecutorRunnable(command));
        } else {
            e.execute(new ChildExecutorRunnable(command));
        }
    }

    private class ChildExecutorRunnable implements Runnable {

        private final Runnable runnable;

        ChildExecutorRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            Thread thread = Thread.currentThread();
            synchronized (startStopLock) {
                activeThreads.add(thread);
            }
            try {
                runnable.run();
            } finally {
                synchronized (startStopLock) {
                    boolean removed = activeThreads.remove(thread);
                    assert removed;
                    if (isTerminated()) {
                        startStopLock.notifyAll();
                    }
                }
            }
        }
    }
}
