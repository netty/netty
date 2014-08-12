/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.embedded;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

import io.netty.util.concurrent.DefaultProgressivePromise;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FailedFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseTask;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.concurrent.SucceededFuture;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.ChannelHandlerInvokerUtil.*;
import static io.netty.util.concurrent.AbstractEventExecutorGroup.*;

final class EmbeddedEventLoop extends AbstractExecutorService implements ChannelHandlerInvoker, EventLoop {

    private final Queue<Runnable> tasks = new ArrayDeque<Runnable>(2);
    private final Set<EmbeddedEventLoop> children = Collections.singleton(this);

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        tasks.add(command);
    }

    void runTasks() {
        for (;;) {
            Runnable task = tasks.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> terminationFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShuttingDown() {
        return false;
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
    public ChannelFuture register(Channel channel) {
        return register(channel, new DefaultChannelPromise(channel, this));
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        channel.unsafe().register(this, promise);
        return promise;
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
    public ChannelHandlerInvoker asInvoker() {
        return this;
    }

    @Override
    public EventExecutor executor() {
        return this;
    }

    @Override
    public void invokeChannelRegistered(ChannelHandlerContext ctx) {
        invokeChannelRegisteredNow(ctx);
    }

    @Override
    public void invokeChannelUnregistered(ChannelHandlerContext ctx) {
        invokeChannelUnregisteredNow(ctx);
    }

    @Override
    public void invokeChannelActive(ChannelHandlerContext ctx) {
        invokeChannelActiveNow(ctx);
    }

    @Override
    public void invokeChannelInactive(ChannelHandlerContext ctx) {
        invokeChannelInactiveNow(ctx);
    }

    @Override
    public void invokeExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        invokeExceptionCaughtNow(ctx, cause);
    }

    @Override
    public void invokeUserEventTriggered(ChannelHandlerContext ctx, Object event) {
        invokeUserEventTriggeredNow(ctx, event);
    }

    @Override
    public void invokeChannelRead(ChannelHandlerContext ctx, Object msg) {
        invokeChannelReadNow(ctx, msg);
    }

    @Override
    public void invokeChannelReadComplete(ChannelHandlerContext ctx) {
        invokeChannelReadCompleteNow(ctx);
    }

    @Override
    public void invokeChannelWritabilityChanged(ChannelHandlerContext ctx) {
        invokeChannelWritabilityChangedNow(ctx);
    }

    @Override
    public void invokeBind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        invokeBindNow(ctx, localAddress, promise);
    }

    @Override
    public void invokeConnect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        invokeConnectNow(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void invokeDisconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        invokeDisconnectNow(ctx, promise);
    }

    @Override
    public void invokeClose(ChannelHandlerContext ctx, ChannelPromise promise) {
        invokeCloseNow(ctx, promise);
    }

    @Override
    public void invokeDeregister(ChannelHandlerContext ctx, final ChannelPromise promise) {
        invokeDeregisterNow(ctx, promise);
    }

    @Override
    public void invokeRead(ChannelHandlerContext ctx) {
        invokeReadNow(ctx);
    }

    @Override
    public void invokeWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        invokeWriteNow(ctx, msg, promise);
    }

    @Override
    public void invokeFlush(ChannelHandlerContext ctx) {
        invokeFlushNow(ctx);
    }

    @Override
    public EventLoop next() {
        return this;
    }

    @Override
    public EventLoop unwrap() {
        return this;
    }

    @Override
    public EventLoopGroup parent() {
        return null;
    }

    @Override
    public Set<EmbeddedEventLoop> children() {
        return children;
    }

    @Override
    public Future<?> shutdownGracefully() {
        return shutdownGracefully(DEFAULT_SHUTDOWN_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new DefaultPromise<V>(this);
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return new DefaultProgressivePromise<V>(this);
    }

    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        return new SucceededFuture<V>(this, result);
    }

    @Override
    public <V> Future<V> newFailedFuture(Throwable cause) {
        return new FailedFuture<V>(this, cause);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return (Future<?>) super.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return (Future<T>) super.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return (Future<T>) super.submit(task);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new PromiseTask<T>(this, runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new PromiseTask<T>(this, callable);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }
}
