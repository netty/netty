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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.util.concurrent.AbstractEventExecutor.*;


/**
 * Abstract base class for {@link EventExecutorGroup} implementations.
 * 实现 EventExecutorGroup 接口，EventExecutorGroup中有EventExecutor ( 事件执行器 )的分组抽象类。
 */
public abstract class AbstractEventExecutorGroup implements EventExecutorGroup {

    /**
     * 功能描述: <br>
     * 〈 提交一个线程任务到EventExecutor 上执行〉
     *
     * @param:
     * @return:io.netty.util.concurrent.Future<?>
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:19
     */
    @Override
    public Future<?> submit(Runnable task) {

        return next().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return next().submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return next().submit(task);
    }

    /**
     * 功能描述: <br>
     * 〈提交一个定时任务到 EventExecutor 中。代码如下：〉
     *
     * @param:
     * @return:io.netty.util.concurrent.ScheduledFuture<?>
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:20
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {

        return next().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return next().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return next().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return next().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    /**
     * 功能描述: <br>
     * 〈关闭 EventExecutorGroup 〉
     *
     * @param:
     * @return:io.netty.util.concurrent.Future<?>
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:28
     */
    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT/*2*/, TimeUnit.SECONDS/*15*/);
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public abstract void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    /**
     * 功能描述: <br>
     * 〈在 EventExecutor 中执行一个普通任务。〉
     *
     * @param:
     * @return:void
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:22
     */
    @Override
    public void execute(Runnable command) {
        next().execute(command);
    }

    /**
     * 功能描述: <br>
     * 〈在 EventExecutor 中执行多个普通任务〉
     * 执行的 EventExecutor ，通过 #next() 方法选择。并且，多个任务使用同一个 EventExecutor 。
     *
     * @param:
     * @return:java.util.List<java.util.concurrent.Future<T>>
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:22
     */
    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return next().invokeAll(tasks);
    }

    /**
     * 功能描述: <br>
     * 〈在 EventExecutor 中执行多个普通任务〉
     * 执行的 EventExecutor ，通过 #next() 方法选择。并且，多个任务使用同一个 EventExecutor 。
     *
     * @param:
     * @return:java.util.List<java.util.concurrent.Future<T>>
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:23
     */
    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {

        return next().invokeAll(tasks, timeout, unit);
    }

    /**
     * 功能描述: <br>
     * 〈在 EventExecutor 中执行多个普通任务，有一个执行完成即可。代码如下〉
     *
     * @param:
     * @return:T
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/16 22:23
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return next().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return next().invokeAny(tasks, timeout, unit);
    }


}
