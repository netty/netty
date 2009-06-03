/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.util;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.netty.util.internal.MapBackedSet;

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
 * ExecutorService virtualExecutor = new VirtualExecutorService(globalExecutor);
 *
 * ChannelFactory factory =
 *         new NioServerSocketChannelFactory(virtualExecutor, virtualExecutor);
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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
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

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        if (s != null) {
            return s.invokeAll(tasks);
        } else {
            return super.invokeAll(tasks);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (s != null) {
            return s.invokeAll(tasks, timeout, unit);
        } else {
            return super.invokeAll(tasks, timeout, unit);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        if (s != null) {
            return s.invokeAny(tasks);
        } else {
            return super.invokeAny(tasks);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        if (s != null) {
            return s.invokeAny(tasks, timeout, unit);
        } else {
            return super.invokeAny(tasks, timeout, unit);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (s != null) {
            return s.submit(task);
        } else {
            return super.submit(task);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (s != null) {
            return s.submit(task);
        } else {
            return super.submit(task);
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (s != null) {
            return s.submit(task, result);
        } else {
            return super.submit(task, result);
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
