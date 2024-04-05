/*
 * Copyright 2012 The Netty Project
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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The {@link EventExecutorGroup} is responsible for providing the {@link EventExecutor}'s to use
 * via its {@link #next()} method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 * <p>
 * {@link EventExecutorGroup} 负责通过其 {@link #next()} 方法提供 {@link EventExecutor} 以供使用。
 * 除此之外，它还负责处理它们的生命周期，并允许以全球方式关闭它们。
 * <p>
 * 看类名，意思是事件执行组. 继承了{@link ScheduledExecutorService}，看起来有执行定时任务的功能
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * Returns {@code true} if and only if all {@link EventExecutor}s managed by this {@link EventExecutorGroup}
     * are being {@linkplain #shutdownGracefully() shut down gracefully} or was {@linkplain #isShutdown() shut down}.
     * <p></p>
     * 当且仅当此 {@link EventExecutorGroup} 管理的所有 {@link EventExecutor} 都处于 {@linkplain #shutdownGracefully() 正常关闭}
     * 或被 {@linkplain #isShutdown() 关闭}时，返回 {@code true}。
     */
    boolean isShuttingDown();

    /**
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     * {@link #shutdownGracefully(long, long, TimeUnit)}的快捷方法，具有合理的默认值。
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully();

    /**
     * Signals this executor that the caller wants the executor to be shut down.  Once this method is called,
     * {@link #isShuttingDown()} starts to return {@code true}, and the executor prepares to shut itself down.
     * Unlike {@link #shutdown()}, graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i>
     * (usually a couple seconds) before it shuts itself down.  If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     * <p></p>
     * 向此执行程序发出信号，表明调用方希望关闭执行程序。调用此方法后，{@link #isShuttingDown()} 开始返回 {@code true}，并且执行器准备自行关闭。
     * 与 {@link #shutdown()} 不同，正常关闭可确保在自行<i>关闭之前的“静默期”</i>（通常为几秒钟）内不提交任何任务。
     * 如果在静默期内提交了任务，则保证该任务被接受，并且静默期将重新开始。
     *
     * @param quietPeriod the quiet period as described in the documentation  文档中描述的静默期
     * @param timeout     the maximum amount of time to wait until the executor is {@linkplain #shutdown()}
     *                    regardless if a task was submitted during the quiet period  等待执行程序为{@link #shutdown()}的最长时间，
     *                    无论任务是否在静默期内提交
     * @param unit        the unit of {@code quietPeriod} and {@code timeout} {@code quietPeriod} 和 {@code timeout} 的单位
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * Returns the {@link Future} which is notified when all {@link EventExecutor}s managed by this
     * {@link EventExecutorGroup} have been terminated.
     * <p></p>
     * 返回 {@link Future}，当此 {@link EventExecutorGroup}管理的所有 {@link EventExecutor}都已终止时，将通知该 { Future}。
     */
    Future<?> terminationFuture();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    void shutdown();

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    /**
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
     * 返回由此 {@link EventExecutorGroup} 管理的 {@link EventExecutor} 之一。
     */
    EventExecutor next();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
