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

import io.netty5.buffer.Buffer;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

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
        when(eventLoop.newPromise()).thenReturn(INSTANCE.newPromise());
        when(eventLoop.isCompatible(any())).thenReturn(true);
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
        when(eventLoop.newPromise()).thenAnswer(inv -> INSTANCE.newPromise());
        when(eventLoop.isCompatible(any())).thenReturn(true);

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
        channel.deregister();

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
        when(eventLoop.isCompatible(any())).thenReturn(true);
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
        when(eventLoop.newPromise()).thenAnswer(inv -> INSTANCE.newPromise());
        doAnswer(invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
        when(eventLoop.isCompatible(any())).thenReturn(true);

        final Channel channel = new TestChannel(eventLoop) {
            private boolean open = true;
            private boolean active;

            @Override
            protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initalData) {
                active = true;
                return true;
            }

            @Override
            protected void doClose()  {
                active = false;
                open = false;
            }

            @Override
            protected void doWriteNow(WriteSink writeHandle) throws Exception {
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
            channel.connect(new InetSocketAddress(NetUtil.LOCALHOST, 8888)).asStage().sync();
            assertSame(ioException, channel.writeAndFlush("").asStage().getCause());

            assertClosedChannelException(channel.writeAndFlush(""), ioException);
            assertClosedChannelException(channel.write(""), ioException);
            assertClosedChannelException(channel.bind(new InetSocketAddress(NetUtil.LOCALHOST, 8888)), ioException);
        } finally {
            channel.close();
        }
    }

    private static void assertClosedChannelException(Future<Void> future, IOException expected)
            throws InterruptedException {
        Throwable cause = future.asStage().getCause();
        assertTrue(cause instanceof ClosedChannelException);
        assertSame(expected, cause.getCause());
    }

    private static void registerChannel(Channel channel) throws Exception {
        when(channel.executor().register(any(IoHandle.class))).thenReturn(
                INSTANCE.newSucceededFuture(new TestIoRegistration()));
        channel.register().asStage().sync(); // Cause any exceptions to be thrown
    }

    private static final class TestIoRegistration extends AtomicBoolean implements IoRegistration {
        @Override
        public long submit(IoOps ops) {
            return 0;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void cancel() {
            // NOOP.
        }

        @Override
        public IoHandler ioHandler() {
            return null;
        }
    }
    private static class TestChannel extends AbstractChannel<Channel, SocketAddress, SocketAddress> {

        private static final class TestIoHandle implements IoHandle {
            static final TestIoHandle INSTANCE = new TestIoHandle();
            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                // noop
            }

            @Override
            public void close() {
                // noop
            }
        }
        TestChannel(EventLoop eventLoop) {
            super(null, eventLoop, false, TestIoHandle.class);
        }

        @Override
        protected IoHandle ioHandle() {
            return TestIoHandle.INSTANCE;
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
        protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initalData) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean doFinishConnect(SocketAddress requestedRemoteAddress) {
            throw new UnsupportedOperationException();
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
        protected void doRead(boolean wasReadPendingAlready) { }

        @Override
        protected boolean doReadNow(ReadSink readSink) {
            return false;
        }

        @Override
        protected void doWriteNow(WriteSink writeSink) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void doShutdown(ChannelShutdownDirection direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown(ChannelShutdownDirection direction) {
            return !isActive();
        }
    }
}
