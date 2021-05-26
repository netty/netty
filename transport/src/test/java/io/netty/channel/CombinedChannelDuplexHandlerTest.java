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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CombinedChannelDuplexHandlerTest {

    private static final Object MSG = new Object();
    private static final SocketAddress ADDRESS = new InetSocketAddress(0);

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
        final Queue<Event> queue = new ArrayDeque<Event>();

        ChannelInboundHandler inboundHandler = new ChannelInboundHandlerAdapter() {
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
        };

        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                inboundHandler, new ChannelOutboundHandlerAdapter());

        EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.pipeline().fireChannelWritabilityChanged();
        channel.pipeline().fireUserEventTriggered(MSG);
        channel.pipeline().fireChannelRead(MSG);
        channel.pipeline().fireChannelReadComplete();

        assertEquals(Event.HANDLER_ADDED, queue.poll());
        assertEquals(Event.REGISTERED, queue.poll());
        assertEquals(Event.ACTIVE, queue.poll());
        assertEquals(Event.CHANNEL_WRITABILITY_CHANGED, queue.poll());
        assertEquals(Event.USER_EVENT_TRIGGERED, queue.poll());
        assertEquals(Event.CHANNEL_READ, queue.poll());
        assertEquals(Event.CHANNEL_READ_COMPLETE, queue.poll());

        handler.removeInboundHandler();
        assertEquals(Event.HANDLER_REMOVED, queue.poll());

        // These should not be handled by the inboundHandler anymore as it was removed before
        channel.pipeline().fireChannelWritabilityChanged();
        channel.pipeline().fireUserEventTriggered(MSG);
        channel.pipeline().fireChannelRead(MSG);
        channel.pipeline().fireChannelReadComplete();

        // Should have not received any more events as it was removed before via removeInboundHandler()
        assertTrue(queue.isEmpty());
        assertTrue(channel.finish());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testOutboundEvents() {
        final Queue<Event> queue = new ArrayDeque<Event>();

        ChannelInboundHandler inboundHandler = new ChannelInboundHandlerAdapter();
        ChannelOutboundHandler outboundHandler = new ChannelOutboundHandlerAdapter() {
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
                queue.add(Event.BIND);
            }

            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) {
                queue.add(Event.CONNECT);
            }

            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
                queue.add(Event.DISCONNECT);
            }

            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                queue.add(Event.CLOSE);
            }

            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
                queue.add(Event.DEREGISTER);
            }

            @Override
            public void read(ChannelHandlerContext ctx) {
                queue.add(Event.READ);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                queue.add(Event.WRITE);
            }

            @Override
            public void flush(ChannelHandlerContext ctx) {
                queue.add(Event.FLUSH);
            }
        };

        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        inboundHandler, outboundHandler);

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst(handler);

        doOutboundOperations(channel);

        assertEquals(Event.HANDLER_ADDED, queue.poll());
        assertEquals(Event.BIND, queue.poll());
        assertEquals(Event.CONNECT, queue.poll());
        assertEquals(Event.WRITE, queue.poll());
        assertEquals(Event.FLUSH, queue.poll());
        assertEquals(Event.READ, queue.poll());
        assertEquals(Event.CLOSE, queue.poll());
        assertEquals(Event.CLOSE, queue.poll());
        assertEquals(Event.DEREGISTER, queue.poll());

        handler.removeOutboundHandler();
        assertEquals(Event.HANDLER_REMOVED, queue.poll());

        // These should not be handled by the inboundHandler anymore as it was removed before
        doOutboundOperations(channel);

        // Should have not received any more events as it was removed before via removeInboundHandler()
        assertTrue(queue.isEmpty());
        assertTrue(channel.finish());
        assertTrue(queue.isEmpty());
    }

    private static void doOutboundOperations(Channel channel) {
        channel.pipeline().bind(ADDRESS);
        channel.pipeline().connect(ADDRESS);
        channel.pipeline().write(MSG);
        channel.pipeline().flush();
        channel.pipeline().read();
        channel.pipeline().disconnect();
        channel.pipeline().close();
        channel.pipeline().deregister();
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testPromisesPassed() {
        ChannelOutboundHandler outboundHandler = new ChannelOutboundHandlerAdapter() {
            @Override
            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                             ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                promise.setSuccess();
            }
        };
        EmbeddedChannel ch = new EmbeddedChannel(outboundHandler,
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter()));
        ChannelPipeline pipeline = ch.pipeline();

        ChannelPromise promise = ch.newPromise();
        pipeline.connect(new InetSocketAddress(0), null, promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.bind(new InetSocketAddress(0), promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.close(promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.disconnect(promise);
        promise.syncUninterruptibly();

        promise = ch.newPromise();
        pipeline.write("test", promise);
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
}
