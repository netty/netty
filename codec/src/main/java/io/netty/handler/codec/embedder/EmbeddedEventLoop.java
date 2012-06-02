package io.netty.handler.codec.embedder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventExecutor;
import io.netty.channel.EventLoop;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class EmbeddedEventLoop extends AbstractExecutorService implements
        EventLoop, EventExecutor.Unsafe {

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
            TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
            TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
            long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
            long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        // NOOP
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
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
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        Thread.sleep(unit.toMillis(timeout));
        return false;
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public ChannelFuture register(Channel channel) {
        return register(channel, channel.newFuture());
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        channel.unsafe().register(this, future);
        return future;
    }

    @Override
    public boolean inEventLoop() {
        return true;
    }

    @Override
    public EventLoop parent() {
        return null;
    }

    @Override
    public Unsafe unsafe() {
        return this;
    }

    @Override
    public EventExecutor nextChild() {
        return this;
    }
}
