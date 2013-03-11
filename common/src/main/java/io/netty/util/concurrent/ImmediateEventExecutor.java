/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * {@link EventExecutor} which executes the command in the caller thread.
 */
public final class ImmediateEventExecutor extends AbstractEventExecutor {

    public ImmediateEventExecutor(TaskScheduler scheduler) {
        super(scheduler);
    }

    @Override
    public EventExecutorGroup parent() {
        return null;
    }

    @Override
    public boolean inEventLoop() {
        return true;
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public void shutdown() {
        // NOOP
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        FutureTask<T> future = new FutureTask<T>(task);
        future.run();
        return future;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        FutureTask<T> future = new FutureTask<T>(task, result);
        future.run();
        return future;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        FutureTask<?> future = new FutureTask(task, null);
        future.run();
        return future;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        List<Future<T>> futures = new ArrayList<Future<T>>();
        for (Callable<T> task: tasks) {
            futures.add(submit(task));
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                                              long timeout, TimeUnit unit) {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }

        List<Future<T>> futures = new ArrayList<Future<T>>();
        for (Callable<T> task: tasks) {
            futures.add(submit(task));
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must be non empty");
        }
        return invokeAll(tasks).get(0).get();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must be non empty");
        }
        return invokeAll(tasks).get(0).get();
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        command.run();
    }
}
