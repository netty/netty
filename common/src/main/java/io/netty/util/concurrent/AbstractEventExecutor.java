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

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public abstract class AbstractEventExecutor extends AbstractExecutorService implements EventExecutor {
    private final Future succeededFuture = new SucceededFuture(this);
    private final TaskScheduler scheduler;

    protected AbstractEventExecutor(TaskScheduler scheduler) {
        if (scheduler == null) {
            throw new NullPointerException("scheduler");
        }
        this.scheduler = scheduler;
    }

    @Override
    public EventExecutor next() {
        return this;
    }

    @Override
    public Promise newPromise() {
        return new DefaultPromise(this);
    }

    @Override
    public Future newSucceededFuture() {
        return succeededFuture;
    }

    @Override
    public Future newFailedFuture(Throwable cause) {
        return new FailedFuture(this, cause);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return scheduler.schedule(this, command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return scheduler.schedule(this, callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(this, command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(this, command, initialDelay, delay, unit);
    }
}
