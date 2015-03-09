/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel;

import io.netty.util.internal.CallableEventExecutorAdapter;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PausableEventExecutor;
import io.netty.util.concurrent.ProgressivePromise;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.RunnableEventExecutorAdapter;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class PausableChannelEventExecutor implements PausableEventExecutor, ChannelHandlerInvoker {

    abstract Channel channel();

    abstract ChannelHandlerInvoker unwrapInvoker();

    @Override
    public void invokeFlush(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeFlush(ctx);
    }

    @Override
    public EventExecutor executor() {
        return this;
    }

    @Override
    public void invokeChannelRegistered(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelRegistered(ctx);
    }

    @Override
    public void invokeChannelUnregistered(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelUnregistered(ctx);
    }

    @Override
    public void invokeChannelActive(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelActive(ctx);
    }

    @Override
    public void invokeChannelInactive(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelInactive(ctx);
    }

    @Override
    public void invokeExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        unwrapInvoker().invokeExceptionCaught(ctx, cause);
    }

    @Override
    public void invokeUserEventTriggered(ChannelHandlerContext ctx, Object event) {
        unwrapInvoker().invokeUserEventTriggered(ctx, event);
    }

    @Override
    public void invokeChannelRead(ChannelHandlerContext ctx, Object msg) {
        unwrapInvoker().invokeChannelRead(ctx, msg);
    }

    @Override
    public void invokeChannelReadComplete(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelReadComplete(ctx);
    }

    @Override
    public void invokeChannelWritabilityChanged(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeChannelWritabilityChanged(ctx);
    }

    @Override
    public void invokeBind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        unwrapInvoker().invokeBind(ctx, localAddress, promise);
    }

    @Override
    public void invokeConnect(
           ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        unwrapInvoker().invokeConnect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void invokeDisconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        unwrapInvoker().invokeDisconnect(ctx, promise);
    }

    @Override
    public void invokeClose(ChannelHandlerContext ctx, ChannelPromise promise) {
        unwrapInvoker().invokeClose(ctx, promise);
    }

    @Override
    public void invokeDeregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        unwrapInvoker().invokeDeregister(ctx, promise);
    }

    @Override
    public void invokeRead(ChannelHandlerContext ctx) {
        unwrapInvoker().invokeRead(ctx);
    }

    @Override
    public void invokeWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        unwrapInvoker().invokeWrite(ctx, msg, promise);
    }

    @Override
    public EventExecutor next() {
        return unwrap().next();
    }

    @Override
    public <E extends EventExecutor> Set<E> children() {
        return unwrap().children();
    }

    @Override
    public EventExecutorGroup parent() {
        return unwrap().parent();
    }

    @Override
    public boolean inEventLoop() {
        return unwrap().inEventLoop();
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return unwrap().inEventLoop(thread);
    }

    @Override
    public <V> Promise<V> newPromise() {
        return unwrap().newPromise();
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return unwrap().newProgressivePromise();
    }

    @Override
    public <V> Future<V> newSucceededFuture(V result) {
        return unwrap().newSucceededFuture(result);
    }

    @Override
    public <V> Future<V> newFailedFuture(Throwable cause) {
        return unwrap().newFailedFuture(cause);
    }

    @Override
    public boolean isShuttingDown() {
        return unwrap().isShuttingDown();
    }

    @Override
    public Future<?> shutdownGracefully() {
        return unwrap().shutdownGracefully();
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return unwrap().shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    public Future<?> terminationFuture() {
        return unwrap().terminationFuture();
    }

    @Override
    @Deprecated
    public void shutdown() {
        unwrap().shutdown();
    }

    @Override
    @Deprecated
    public List<Runnable> shutdownNow() {
        return unwrap().shutdownNow();
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().submit(task);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }

        return unwrap().schedule(new ChannelRunnableEventExecutor(channel(), command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().schedule(new ChannelCallableEventExecutor<V>(channel(), callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().scheduleAtFixedRate(
                new ChannelRunnableEventExecutor(channel(), command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().scheduleWithFixedDelay(
                new ChannelRunnableEventExecutor(channel(), command), initialDelay, delay, unit);
    }

    @Override
    public boolean isShutdown() {
        return unwrap().isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return unwrap().isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return unwrap().awaitTermination(timeout, unit);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>>
    invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>>
    invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        return unwrap().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        if (!isAcceptingNewTasks()) {
            throw new RejectedExecutionException();
        }
        unwrap().execute(command);
    }

    @Override
    public void close() throws Exception {
        unwrap().close();
    }

    private static final class ChannelCallableEventExecutor<V> implements CallableEventExecutorAdapter<V> {

        final Channel channel;
        final Callable<V> callable;

        ChannelCallableEventExecutor(Channel channel, Callable<V> callable) {
            this.channel = channel;
            this.callable = callable;
        }

        @Override
        public EventExecutor executor() {
            return channel.eventLoop();
        }

        @Override
        public Callable unwrap() {
            return callable;
        }

        @Override
        public V call() throws Exception {
            return callable.call();
        }
    }

    private static final class ChannelRunnableEventExecutor implements RunnableEventExecutorAdapter {

        final Channel channel;
        final Runnable runnable;

        ChannelRunnableEventExecutor(Channel channel, Runnable runnable) {
            this.channel = channel;
            this.runnable = runnable;
        }

        @Override
        public EventExecutor executor() {
            return channel.eventLoop();
        }

        @Override
        public Runnable unwrap() {
            return runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }
}
