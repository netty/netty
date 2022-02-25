/*
 * Copyright 2014 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * https://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel;

import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import static io.netty5.util.concurrent.ImmediateEventExecutor.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbstractChannelTest {

    @Test
    public void ensureInitialRegistrationFiresActive() throws Throwable {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.unsafe()).thenReturn(mock(EventLoop.Unsafe.class));
        when(eventLoop.newPromise()).thenReturn(INSTANCE.newPromise());

        TestChannel channel = new TestChannel(eventLoop);
        // Using spy as otherwise intelliJ will not be able to understand that we dont want to skip the handler
        ChannelHandler handler = spy(new ChannelHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) {
                // NOOP
            }

            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                ctx.fireChannelRegistered();
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.fireChannelActive();
            }
        });
        channel.pipeline().addLast(handler);

        registerChannel(channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));
        verify(handler).channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureSubsequentRegistrationDoesNotFireActive() throws Throwable {
        final EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.unsafe()).thenReturn(mock(EventLoop.Unsafe.class));
        when(eventLoop.newPromise()).thenAnswer(inv -> INSTANCE.newPromise());

        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        final TestChannel channel = new TestChannel(eventLoop);
        // Using spy as otherwise intelliJ will not be able to understand that we dont want to skip the handler
        ChannelHandler handler = spy(new ChannelHandler() {
            @Override
            public void channelRegistered(ChannelHandlerContext ctx) {
                ctx.fireChannelRegistered();
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) {
                ctx.fireChannelUnregistered();
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                ctx.fireChannelActive();
            }
        });

        channel.pipeline().addLast(handler);

        registerChannel(channel);
        channel.unsafe().deregister(channel.newPromise());

        registerChannel(channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));

        // Should register twice
        verify(handler, times(2)) .channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
        verify(handler).channelUnregistered(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureDefaultChannelId() {
        final EventLoop eventLoop = mock(EventLoop.class);
        TestChannel channel = new TestChannel(eventLoop);
        final ChannelId channelId = channel.id();
        assertTrue(channelId instanceof DefaultChannelId);
    }

    @Test
    public void testClosedChannelExceptionCarryIOException() throws Exception {
        final IOException ioException = new IOException();
        final EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);
        when(eventLoop.unsafe()).thenReturn(mock(EventLoop.Unsafe.class));
        when(eventLoop.newPromise()).thenAnswer(inv -> INSTANCE.newPromise());
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        final Channel channel = new TestChannel(eventLoop) {
            private boolean open = true;
            private boolean active;

            @Override
            protected AbstractUnsafe newUnsafe() {
                return new AbstractUnsafe() {
                    @Override
                    public void connect(
                            SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
                        active = true;
                        promise.setSuccess(null);
                    }
                };
            }

            @Override
            protected void doClose()  {
                active = false;
                open = false;
            }

            @Override
            protected void doWrite(ChannelOutboundBuffer in) throws Exception {
                throw ioException;
            }

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };

        try {
            registerChannel(channel);
            channel.connect(new InetSocketAddress(NetUtil.LOCALHOST, 8888)).sync();
            assertSame(ioException, channel.writeAndFlush("").await().cause());

            assertClosedChannelException(channel.writeAndFlush(""), ioException);
            assertClosedChannelException(channel.write(""), ioException);
            assertClosedChannelException(channel.bind(new InetSocketAddress(NetUtil.LOCALHOST, 8888)), ioException);
        } finally {
            channel.close();
        }
    }

    private static void assertClosedChannelException(Future<Void> future, IOException expected)
            throws InterruptedException {
        Throwable cause = future.await().cause();
        assertTrue(cause instanceof ClosedChannelException);
        assertSame(expected, cause.getCause());
    }

    private static void registerChannel(Channel channel) throws Exception {
        channel.register().sync(); // Cause any exceptions to be thrown
    }

    private static class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);

        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel(EventLoop eventLoop) {
            super(null, eventLoop);
        }

        @Override
        public ChannelConfig config() {
            return config;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public ChannelMetadata metadata() {
            return TEST_METADATA;
        }

        @Override
        protected AbstractUnsafe newUnsafe() {
            return new AbstractUnsafe() {
                @Override
                public void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
                    promise.setFailure(new UnsupportedOperationException());
                }
            };
        }

        @Override
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doBind(SocketAddress localAddress) { }

        @Override
        protected void doDisconnect() { }

        @Override
        protected void doClose() { }

        @Override
        protected void doBeginRead() { }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception { }
    }
}
