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
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.*;

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

    @Test(expected = IllegalStateException.class)
    public void testInboundRemoveBeforeAdded() {
        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter());
        handler.removeInboundHandler();
    }

    @Test(expected = IllegalStateException.class)
    public void testOutboundRemoveBeforeAdded() {
        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                        new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter());
        handler.removeOutboundHandler();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInboundHandlerImplementsOutboundHandler() {
        new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                new ChannelDuplexHandler(), new ChannelOutboundHandlerAdapter());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutboundHandlerImplementsInboundHandler() {
        new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>(
                new ChannelInboundHandlerAdapter(), new ChannelDuplexHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void testInitNotCalledBeforeAdded() throws Exception {
        CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler> handler =
                new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>() { };
        handler.handlerAdded(null);
    }

    @Test
    public void testExceptionCaughtBothCombinedHandlers() {
        final Exception exception = new Exception();
        final Queue<ChannelHandler> queue = new ArrayDeque<ChannelHandler>();

        ChannelInboundHandler inboundHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                assertSame(exception, cause);
                queue.add(this);
                ctx.fireExceptionCaught(cause);
            }
        };
        ChannelOutboundHandler outboundHandler = new ChannelOutboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                assertSame(exception, cause);
                queue.add(this);
                ctx.fireExceptionCaught(cause);
            }
        };
        ChannelInboundHandler lastHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.HANDLER_ADDED);
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.HANDLER_REMOVED);
            }

            @Override
            public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.REGISTERED);
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.UNREGISTERED);
            }

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.ACTIVE);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.INACTIVE);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                queue.add(Event.CHANNEL_READ);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.CHANNEL_READ_COMPLETE);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                queue.add(Event.USER_EVENT_TRIGGERED);
            }

            @Override
            public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.CHANNEL_WRITABILITY_CHANGED);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.HANDLER_ADDED);
            }

            @Override
            public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.HANDLER_REMOVED);
            }

            @Override
            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
                    throws Exception {
                queue.add(Event.BIND);
            }

            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) throws Exception {
                queue.add(Event.CONNECT);
            }

            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                queue.add(Event.DISCONNECT);
            }

            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                queue.add(Event.CLOSE);
            }

            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                queue.add(Event.DEREGISTER);
            }

            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                queue.add(Event.READ);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                queue.add(Event.WRITE);
            }

            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
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

    @Test(timeout = 3000)
    public void testPromisesPassed() {
        ChannelOutboundHandler outboundHandler = new ChannelOutboundHandlerAdapter() {
            @Override
            public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
                             ChannelPromise promise) throws Exception {
                promise.setSuccess();
            }

            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) throws Exception {
                promise.setSuccess();
            }

            @Override
            public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                promise.setSuccess();
            }

            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                promise.setSuccess();
            }

            @Override
            public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
                promise.setSuccess();
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
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

    @Test(expected = IllegalStateException.class)
    public void testNotSharable() {
        new CombinedChannelDuplexHandler<ChannelInboundHandler, ChannelOutboundHandler>() {
            @Override
            public boolean isSharable() {
                return true;
            }
        };
    }
}
