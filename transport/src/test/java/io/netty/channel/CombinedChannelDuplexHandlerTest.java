/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class CombinedChannelDuplexHandlerTest {

    private static final Object MSG = new Object();
    private static final SocketAddress LOCAL_ADDRESS = new InetSocketAddress(0);
    private static final SocketAddress REMOTE_ADDRESS = new InetSocketAddress(0);
    private static final Throwable CAUSE = new Throwable();
    private static final Object USER_EVENT = new Object();

    private enum Event {
        REGISTERED,
        UNREGISTERED,
        ACTIVE,
        INACTIVE,
        CHANNEL_READ,
        CHANNEL_READ_COMPLETE,
        EXCEPTION_CAUGHT,
        USER_EVENT_TRIGGERED,
        CHANNEL_WRITABILITY_CHANGED,
        HANDLER_ADDED,
        HANDLER_REMOVED,
        BIND,
        CONNECT,
        WRITE,
        FLUSH,
        READ,
        REGISTER,
        DEREGISTER,
        CLOSE,
        DISCONNECT
    }

    @Test
    public void testInboundRemoveBeforeAdded() {
        final CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter());
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                handler.removeInboundHandler();
            }
        });
    }

    @Test
    public void testOutboundRemoveBeforeAdded() {
        final CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter());
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                handler.removeOutboundHandler();
            }
        });
    }

    @Test
    public void testInboundHandlerImplementsOutboundHandler() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelDuplexHandler(), new ChannelOutboundHandlerAdapter());
            }
        });
    }

    @Test
    public void testOutboundHandlerImplementsInboundHandler() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelDuplexHandler());
            }
        });
    }

    @Test
    public void testInitNotCalledBeforeAdded() {
        final CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>() { };
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                handler.handlerAdded(null);
            }
        });
    }

    @Test
    public void testExceptionCaughtBothCombinedHandlers() {
        final Exception exception = new Exception();
        final Queue<ChannelHandler> queue = new ArrayDeque<ChannelHandler>();

        ChannelInboundHandler inboundHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                assertSame(exception, cause);
                queue.add(this);
                ctx.fireExceptionCaught(cause);
            }
        };
        ChannelOutboundHandler outboundHandler = new ChannelOutboundHandlerAdapter() {
            @SuppressWarnings("deprecation")
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                assertSame(exception, cause);
                queue.add(this);
                ctx.fireExceptionCaught(cause);
            }
        };
        ChannelInboundHandler lastHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                assertSame(exception, cause);
                queue.add(this);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        inboundHandler, outboundHandler), lastHandler);
        channel.pipeline().fireExceptionCaught(exception);
        assertFalse(channel.finish());
        assertSame(inboundHandler, queue.poll());
        assertSame(outboundHandler, queue.poll());
        assertSame(lastHandler, queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testInboundEvents() {
        InboundEventHandler inboundHandler = new InboundEventHandler();

        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                inboundHandler, new ChannelOutboundHandlerAdapter());

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(handler);
        assertEquals(Event.HANDLER_ADDED, inboundHandler.pollEvent());

        doInboundOperations(channel);
        assertInboundOperations(inboundHandler);
        handler.removeInboundHandler();

        assertEquals(Event.HANDLER_REMOVED, inboundHandler.pollEvent());

        // These should not be handled by the inboundHandler anymore as it was removed before
        doInboundOperations(channel);

        // Should have not received any more events as it was removed before via removeInboundHandler()
        assertNull(inboundHandler.pollEvent());
        try {
            channel.checkException();
            fail();
        } catch (Throwable cause) {
            assertSame(CAUSE, cause);
        }

        assertTrue(channel.finish());
        assertNull(inboundHandler.pollEvent());
    }

    @Test
    public void testOutboundEvents() {
        ChannelInboundHandler inboundHandler = new ChannelInboundHandlerAdapter();
        OutboundEventHandler outboundHandler = new OutboundEventHandler();

        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        inboundHandler, outboundHandler);

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new OutboundEventHandler());
        channel.pipeline().addLast(handler);

        assertEquals(Event.HANDLER_ADDED, outboundHandler.pollEvent());

        doOutboundOperations(channel);

        assertOutboundOperations(outboundHandler);

        handler.removeOutboundHandler();

        assertEquals(Event.HANDLER_REMOVED, outboundHandler.pollEvent());

        // These should not be handled by the inboundHandler anymore as it was removed before
        doOutboundOperations(channel);

        // Should have not received any more events as it was removed before via removeInboundHandler()
        assertNull(outboundHandler.pollEvent());
        assertFalse(channel.finish());
        assertNull(outboundHandler.pollEvent());
    }

    private static void doOutboundOperations(Channel channel) {
        channel.pipeline().bind(LOCAL_ADDRESS).syncUninterruptibly();
        channel.pipeline().connect(REMOTE_ADDRESS, LOCAL_ADDRESS).syncUninterruptibly();
        channel.pipeline().write(MSG).syncUninterruptibly();
        channel.pipeline().flush();
        channel.pipeline().read();
        channel.pipeline().disconnect().syncUninterruptibly();
        channel.pipeline().close().syncUninterruptibly();
        channel.pipeline().deregister().syncUninterruptibly();
    }

    private static void assertOutboundOperations(OutboundEventHandler outboundHandler) {
        assertEquals(Event.BIND, outboundHandler.pollEvent());
        assertEquals(Event.CONNECT, outboundHandler.pollEvent());
        assertEquals(Event.WRITE, outboundHandler.pollEvent());
        assertEquals(Event.FLUSH, outboundHandler.pollEvent());
        assertEquals(Event.READ, outboundHandler.pollEvent());
        assertEquals(Event.CLOSE, outboundHandler.pollEvent());
        assertEquals(Event.CLOSE, outboundHandler.pollEvent());
        assertEquals(Event.DEREGISTER, outboundHandler.pollEvent());
    }

    private static void doInboundOperations(Channel channel) {
        channel.pipeline().fireChannelRegistered();
        channel.pipeline().fireChannelActive();
        channel.pipeline().fireChannelRead(MSG);
        channel.pipeline().fireChannelReadComplete();
        channel.pipeline().fireExceptionCaught(CAUSE);
        channel.pipeline().fireUserEventTriggered(USER_EVENT);
        channel.pipeline().fireChannelWritabilityChanged();
        channel.pipeline().fireChannelInactive();
        channel.pipeline().fireChannelUnregistered();
    }

    private static void assertInboundOperations(InboundEventHandler handler) {
        assertEquals(Event.REGISTERED, handler.pollEvent());
        assertEquals(Event.ACTIVE, handler.pollEvent());
        assertEquals(Event.CHANNEL_READ, handler.pollEvent());
        assertEquals(Event.CHANNEL_READ_COMPLETE, handler.pollEvent());
        assertEquals(Event.EXCEPTION_CAUGHT, handler.pollEvent());
        assertEquals(Event.USER_EVENT_TRIGGERED, handler.pollEvent());
        assertEquals(Event.CHANNEL_WRITABILITY_CHANGED, handler.pollEvent());
        assertEquals(Event.INACTIVE, handler.pollEvent());
        assertEquals(Event.UNREGISTERED, handler.pollEvent());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testPromisesPassed() {
        OutboundEventHandler outboundHandler = new OutboundEventHandler();
        EmbeddedChannel ch = new EmbeddedChannel(outboundHandler,
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter()));
        ChannelPipeline pipeline = ch.pipeline();

        ChannelPromise promise = ch.newPromise();
        pipeline.bind(LOCAL_ADDRESS, promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.connect(REMOTE_ADDRESS, LOCAL_ADDRESS, promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.close(promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.disconnect(promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.write(MSG, promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.deregister(promise);
        promise.syncUninterruptibly();
        ch.finish();
    }

    @Test
    public void testNotSharable() {
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                };
            }
        });
    }

    private static final class InboundEventHandler extends ChannelInboundHandlerAdapter {
        private final Queue<Object> queue = new ArrayDeque<Object>();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            queue.add(Event.HANDLER_ADDED);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            queue.add(Event.HANDLER_REMOVED);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            queue.add(Event.REGISTERED);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            queue.add(Event.UNREGISTERED);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            queue.add(Event.ACTIVE);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            queue.add(Event.INACTIVE);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            queue.add(Event.CHANNEL_READ);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            queue.add(Event.CHANNEL_READ_COMPLETE);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            queue.add(Event.USER_EVENT_TRIGGERED);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            queue.add(Event.CHANNEL_WRITABILITY_CHANGED);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            queue.add(Event.EXCEPTION_CAUGHT);
        }

        Event pollEvent() {
            Object o = queue.poll();
            if (o instanceof AssertionError) {
                throw (AssertionError) o;
            }
            return (Event) o;
        }
    }

    private static final class OutboundEventHandler extends ChannelOutboundHandlerAdapter {
        private final Queue<Object> queue = new ArrayDeque<Object>();

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            queue.add(Event.HANDLER_ADDED);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            queue.add(Event.HANDLER_REMOVED);
        }

        @Override
        public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
            try {
                assertSame(LOCAL_ADDRESS, localAddress);
                queue.add(Event.BIND);
                promise.setSuccess();
            } catch (AssertionError e) {
                promise.setFailure(e);
            }
        }

        @Override
        public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                            SocketAddress localAddress, ChannelPromise promise) {
            try {
                assertSame(REMOTE_ADDRESS, remoteAddress);
                assertSame(LOCAL_ADDRESS, localAddress);
                queue.add(Event.CONNECT);
                promise.setSuccess();
            } catch (AssertionError e) {
                promise.setFailure(e);
            }
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
            queue.add(Event.DISCONNECT);
            promise.setSuccess();
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
            queue.add(Event.CLOSE);
            promise.setSuccess();
        }

        @Override
        public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
            queue.add(Event.DEREGISTER);
            promise.setSuccess();
        }

        @Override
        public void read(ChannelHandlerContext ctx) {
            queue.add(Event.READ);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            try {
                assertSame(MSG, msg);
                queue.add(Event.WRITE);
                promise.setSuccess();
            } catch (AssertionError e) {
                promise.setFailure(e);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            queue.add(Event.FLUSH);
        }

        Event pollEvent() {
            Object o = queue.poll();
            if (o instanceof AssertionError) {
                throw (AssertionError) o;
            }
            return (Event) o;
        }
    }
}
