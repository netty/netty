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
package io.netty.channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;

import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AbstractChannelTest {

    @Test
    public void ensureInitialRegistrationFiresActive() throws Throwable {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);

        TestChannel channel = new TestChannel();
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);
        channel.pipeline().addLast(handler);

        registerChannel(eventLoop, channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));
        verify(handler).channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureSubsequentRegistrationDoesNotFireActive() throws Throwable {
        final EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                ((Runnable) invocationOnMock.getArgument(0)).run();
                return null;
            }
        }).when(eventLoop).execute(any(Runnable.class));

        final TestChannel channel = new TestChannel();
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);

        channel.pipeline().addLast(handler);

        registerChannel(eventLoop, channel);
        channel.unsafe().deregister(new DefaultChannelPromise(channel));

        registerChannel(eventLoop, channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));

        // Should register twice
        verify(handler,  times(2)) .channelRegistered(any(ChannelHandlerContext.class));
        verify(handler).channelActive(any(ChannelHandlerContext.class));
        verify(handler).channelUnregistered(any(ChannelHandlerContext.class));
    }

    @Test
    public void ensureDefaultChannelId() {
        TestChannel channel = new TestChannel();
        final ChannelId channelId = channel.id();
        assertTrue(channelId instanceof DefaultChannelId);
    }

    @Test
    public void testClosedChannelExceptionCarryIOException() throws Exception {
        final IOException ioException = new IOException();
        final Channel channel = new TestChannel() {
            private boolean open = true;
            private boolean active;

            @Override
            protected AbstractUnsafe newUnsafe() {
                return new AbstractUnsafe() {
                    @Override
                    public void connect(
                            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                        active = true;
                        promise.setSuccess();
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

        EventLoop loop = new DefaultEventLoop();
        try {
            registerChannel(loop, channel);
            channel.connect(new InetSocketAddress(NetUtil.LOCALHOST, 8888)).sync();
            assertSame(ioException, channel.writeAndFlush("").await().cause());

            assertClosedChannelException(channel.writeAndFlush(""), ioException);
            assertClosedChannelException(channel.write(""), ioException);
            assertClosedChannelException(channel.bind(new InetSocketAddress(NetUtil.LOCALHOST, 8888)), ioException);
        } finally {
            channel.close();
            loop.shutdownGracefully();
        }
    }

    private static void assertClosedChannelException(ChannelFuture future, IOException expected)
            throws InterruptedException {
        Throwable cause = future.await().cause();
        assertTrue(cause instanceof ClosedChannelException);
        assertSame(expected, cause.getCause());
    }

    private static void registerChannel(EventLoop eventLoop, Channel channel) throws Exception {
        DefaultChannelPromise future = new DefaultChannelPromise(channel);
        channel.unsafe().register(eventLoop, future);
        future.sync(); // Cause any exceptions to be thrown
    }

    private static class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);

        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel() {
            super(null);
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
                public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                    promise.setFailure(new UnsupportedOperationException());
                }
            };
        }

        @Override
        protected boolean isCompatible(EventLoop loop) {
            return true;
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
