/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel.socket.nio;

import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.AbstractNioChannel;
import io.netty5.channel.nio.NioHandler;
import io.netty5.util.concurrent.AbstractEventExecutor;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public abstract class AbstractNioChannelTest<T extends AbstractNioChannel> {

    protected abstract T newNioChannel(EventLoopGroup group);

    protected abstract NetworkChannel jdkChannel(T channel);

    protected abstract SocketOption<?> newInvalidOption();

    @Test
    public void testNioChannelOption() throws IOException {
        EventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        T channel = newNioChannel(eventLoopGroup);
        try {
            NetworkChannel jdkChannel = jdkChannel(channel);
            ChannelOption<Boolean> option = NioChannelOption.of(StandardSocketOptions.SO_REUSEADDR);
            boolean value1 = jdkChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            boolean value2 = channel.config().getOption(option);

            assertEquals(value1, value2);

            channel.config().setOption(option, !value2);
            boolean value3 = jdkChannel.getOption(StandardSocketOptions.SO_REUSEADDR);
            boolean value4 = channel.config().getOption(option);
            assertEquals(value3, value4);
            assertNotEquals(value1, value4);
        } finally {
            channel.close().syncUninterruptibly();
            eventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testInvalidNioChannelOption() {
        EventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        T channel = newNioChannel(eventLoopGroup);
        try {
            ChannelOption<?> option = NioChannelOption.of(newInvalidOption());
            assertFalse(channel.config().setOption(option, null));
            assertNull(channel.config().getOption(option));
        } finally {
            channel.close().syncUninterruptibly();
            eventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testGetOptions()  {
        EventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        T channel = newNioChannel(eventLoopGroup);
        try {
            channel.config().getOptions();
        } finally {
            channel.close().syncUninterruptibly();
            eventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testWrapping() {
        EventLoopGroup eventLoopGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        final EventLoop eventLoop = eventLoopGroup.next();

        class WrappedEventLoop extends AbstractEventExecutor implements EventLoop {
            private final EventLoop eventLoop;

            WrappedEventLoop(EventLoop eventLoop) {
                this.eventLoop = eventLoop;
            }

            @Override
            @Test
            public EventLoop next() {
                return this;
            }

            @Override
            public Unsafe unsafe() {
                return eventLoop.unsafe();
            }

            @Override
            public boolean inEventLoop(Thread thread) {
                return eventLoop.inEventLoop(thread);
            }

            @Override
            public boolean isShuttingDown() {
                return eventLoop.isShuttingDown();
            }

            @Override
            public Future<Void> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                return eventLoop.shutdownGracefully(quietPeriod, timeout, unit);
            }

            @Override
            public Future<Void> terminationFuture() {
                return eventLoop.terminationFuture();
            }

            @Override
            public boolean isShutdown() {
                return eventLoop.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return eventLoop.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return eventLoop.awaitTermination(timeout, unit);
            }

            @Override
            public void execute(Runnable task) {
                eventLoop.execute(task);
            }

            @Override
            public Future<Void> schedule(Runnable task, long delay, TimeUnit unit) {
                return eventLoop.schedule(task, delay, unit);
            }

            @Override
            public <V> Future<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
                return eventLoop.schedule(task, delay, unit);
            }

            @Override
            public Future<Void> scheduleAtFixedRate(
                    Runnable task, long initialDelay, long period, TimeUnit unit) {
                return eventLoop.scheduleAtFixedRate(task, initialDelay, period, unit);
            }

            @Override
            public Future<Void> scheduleWithFixedDelay(
                    Runnable task, long initialDelay, long delay, TimeUnit unit) {
                return eventLoop.scheduleWithFixedDelay(task, initialDelay, delay, unit);
            }
        }

        EventLoop wrapped = new WrappedEventLoop(eventLoop);
        T channel = newNioChannel(wrapped);
        channel.register().syncUninterruptibly();

        assertSame(wrapped, channel.executor());
        channel.close().syncUninterruptibly();
        eventLoopGroup.shutdownGracefully();
    }
}
