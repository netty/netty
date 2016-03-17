/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.channel.AbstractEventLoop;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.ChannelHandlerInvokerUtil.*;

/**
 * The sole purpose of this class it to work around that the EmbeddedEventLoop of
 * EmbeddedChannel throws an UnsupportedOperation exception for scheduleAtFixedRate.
 *
 * Instances of this class will delegate all call except the schudule methods to the
 * passed in EventLoop which for testing purposes will be the EmbeddedEventLoop.
 * For the schedule methods, the passed in {@link SchedulerExecutor} will be used
 * to provide the implementation to allow testing of different senarios.
 *
 */
public class StubEmbeddedEventLoop extends AbstractEventLoop implements ChannelHandlerInvoker {

    private final EventLoop delegate;
    private final SchedulerExecutor schedulerExecutor;

    public StubEmbeddedEventLoop(final EventLoop delegate, final SchedulerExecutor schedulerExecutor) {
        super(delegate);
        this.delegate = delegate;
        this.schedulerExecutor = schedulerExecutor;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedulerExecutor.schedule(command, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return schedulerExecutor.scheduleAtFixedRate(runnable, initialDelay, period, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return schedulerExecutor.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return schedulerExecutor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return delegate.shutdownGracefully(quietPeriod, timeout, unit);
    }

    @Override
    public Future<?> terminationFuture() {
        return delegate.terminationFuture();
    }

    @Override
    @Deprecated
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public boolean isShuttingDown() {
        return delegate.isShuttingDown();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public boolean inEventLoop() {
        return delegate.inEventLoop();
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return delegate.inEventLoop(thread);
    }

    @Override
    public EventLoop next() {
        return delegate.next();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return delegate.register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelPromise promise) {
        return delegate.register(channel, promise);
    }

    @Override
    public ChannelHandlerInvoker asInvoker() {
        return this;
    }

    @Override
    public EventLoopGroup parent() {
        return delegate.parent();
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
    public void invokeDeregister(ChannelHandlerContext ctx, ChannelPromise promise) {
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

    /**
     * This interface declares the schedule methods of {@link ScheduledExecutorService} to
     * enable different implementations for testing.
     */
    public interface SchedulerExecutor {

        ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

        <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

        ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

        ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
    }

}
