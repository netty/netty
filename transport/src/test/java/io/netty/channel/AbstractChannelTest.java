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
import java.util.concurrent.Executors;

import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class AbstractChannelTest {

    @Test
    public void ensureInitialRegistrationFiresActive() throws Throwable {
        EventLoop eventLoop = mock(EventLoop.class);
        // This allows us to have a single-threaded test
        when(eventLoop.inEventLoop()).thenReturn(true);

        TestChannel channel = new TestChannel(eventLoop);
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);
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

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) {
                ((Runnable) invocationOnMock.getArgument(0)).run();
                return null;
            }
        }).when(eventLoop).execute(any(Runnable.class));

        final TestChannel channel = new TestChannel(eventLoop);
        ChannelInboundHandler handler = mock(ChannelInboundHandler.class);

        channel.pipeline().addLast(handler);

        registerChannel(channel);
        channel.deregister(new DefaultChannelPromise(channel));

        registerChannel(channel);

        verify(handler).handlerAdded(any(ChannelHandlerContext.class));

        // Should register twice
        verify(handler,  times(2)) .channelRegistered(any(ChannelHandlerContext.class));
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
    @EnabledForJreRange(min = JRE.JAVA_9)
    void processIdWithProcessHandleJava9() {
        ClassLoader loader = PlatformDependent.getClassLoader(DefaultChannelId.class);
        int processHandlePid = DefaultChannelId.processHandlePid(loader);
        assertTrue(processHandlePid != -1);
        assertEquals(DefaultChannelId.jmxPid(loader), processHandlePid);
        assertEquals(DefaultChannelId.defaultProcessId(), processHandlePid);
    }

    @Test
    @EnabledForJreRange(max = JRE.JAVA_8)
    void processIdWithJmxPrejava9() {
        ClassLoader loader = PlatformDependent.getClassLoader(DefaultChannelId.class);
        int processHandlePid = DefaultChannelId.processHandlePid(loader);
        assertEquals(-1, processHandlePid);
        assertEquals(DefaultChannelId.defaultProcessId(), DefaultChannelId.jmxPid(loader));
    }

    @Test
    public void testClosedChannelExceptionCarryIOException() throws Exception {

        EventLoop loop = new SingleThreadEventLoop(null, Executors.defaultThreadFactory(), true) {

            @Override
            protected void run() {
                for (;;) {
                    Runnable task = takeTask();
                    if (task != null) {
                        runTask(task);
                        updateLastExecutionTime();
                    }

                    if (confirmShutdown()) {
                        break;
                    }
                }
            }

            @Override
            public Future<IoRegistration> register(IoHandle handle) {
                return newSucceededFuture(new IoRegistration() {
                    @Override
                    public <T> T attachment() {
                        return null;
                    }

                    @Override
                    public long submit(IoOps ops) {
                        return 0;
                    }

                    @Override
                    public boolean isValid() {
                        return false;
                    }

                    @Override
                    public boolean cancel() {
                        return false;
                    }
                });
            }

            @Override
            public boolean isCompatible(Class<? extends IoHandle> handleType) {
                return true;
            }

            @Override
            public boolean isIoType(Class<? extends IoHandler> handlerType) {
                return true;
            }
        };
        final IOException ioException = new IOException();
        final Channel channel = new TestChannel(loop) {
            private boolean open = true;
            private boolean active;

            @Override
            protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                active = true;
                promise.setSuccess();
            }

            @Override
            protected void doClose(ChannelPromise promise)  {
                active = false;
                open = false;
                promise.setSuccess();
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
            loop.shutdownGracefully();
        }
    }

    private static void assertClosedChannelException(ChannelFuture future, IOException expected)
            throws InterruptedException {
        Throwable cause = future.await().cause();
        assertTrue(cause instanceof ClosedChannelException);
        assertSame(expected, cause.getCause());
    }

    private static void registerChannel(Channel channel) throws Exception {
        DefaultChannelPromise future = new DefaultChannelPromise(channel);
        channel.register(future);
        future.sync(); // Cause any exceptions to be thrown
    }

    private static class TestChannel extends AbstractChannel {
        private static final ChannelMetadata TEST_METADATA = new ChannelMetadata(false);

        private final ChannelConfig config = new DefaultChannelConfig(this);

        TestChannel(EventLoop eventLoop) {
            super(eventLoop, null, null);
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
        protected SocketAddress localAddress0() {
            return null;
        }

        @Override
        protected SocketAddress remoteAddress0() {
            return null;
        }

        @Override
        protected void doDeregister(ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doRegister(ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doBind(SocketAddress localAddress, ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doDisconnect(ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doClose(ChannelPromise promise) {
            promise.setSuccess();
        }

        @Override
        protected void doBeginRead() { }

        @Override
        protected void doWrite(ChannelOutboundBuffer in) throws Exception { }
    }
}
