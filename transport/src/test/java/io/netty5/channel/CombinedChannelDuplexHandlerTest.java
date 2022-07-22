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
package io.netty5.channel;

import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

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
        CHANNEL_EXCEPTION_CAUGHT,
        CHANNEL_INBOUND_EVENT,
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
        DISCONNECT,
        SEND_OUTBOUND_EVENT
    }

    @Test
    public void testInboundRemoveBeforeAdded() {
        CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler> handler =
                new CombinedChannelDuplexHandler<>(
                        new ChannelHandler() { }, new ChannelHandler() { });
        assertThrows(IllegalStateException.class, handler::removeInboundHandler);
    }

    @Test
    public void testOutboundRemoveBeforeAdded() {
        CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler> handler =
                new CombinedChannelDuplexHandler<>(
                        new ChannelHandler() { }, new ChannelHandler() { });
        assertThrows(IllegalStateException.class, handler::removeOutboundHandler);
    }

    @Test
    public void testInboundHandlerImplementsOutboundHandler() {
        assertThrows(IllegalArgumentException.class,
            () -> new CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler>(
                  new ChannelHandler() {
                      @Override
                      public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
                          return ctx.newFailedFuture(new UnsupportedOperationException());
                      }
                  }, new ChannelHandler() { }));
    }

    @Test
    public void testOutboundHandlerImplementsInboundHandler() {
        assertThrows(IllegalArgumentException.class,
              () -> new CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler>(
                    new ChannelHandler() { }, new ChannelHandler() {
                      @Override
                      public void channelActive(ChannelHandlerContext ctx) {
                          // NOOP
                      }
                  }));
    }

    @Test
    public void testInitNotCalledBeforeAdded() throws Exception {
        CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler> handler =
                new CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler>() { };
        assertThrows(IllegalStateException.class, () -> handler.handlerAdded(null));
    }

    @Test
    public void testExceptionCaught() {
        final Exception exception = new Exception();
        final Queue<ChannelHandler> queue = new ArrayDeque<>();

        ChannelHandler inboundHandler = new ChannelHandler() {
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                assertSame(exception, cause);
                queue.add(this);
                ctx.fireChannelExceptionCaught(cause);
            }
        };
        ChannelHandler lastHandler = new ChannelHandler() {
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                assertSame(exception, cause);
                queue.add(this);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(
                new CombinedChannelDuplexHandler<>(
                        inboundHandler, new ChannelHandler() { }), lastHandler);
        channel.pipeline().fireChannelExceptionCaught(exception);
        assertFalse(channel.finish());
        assertSame(inboundHandler, queue.poll());
        assertSame(lastHandler, queue.poll());
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testInboundEvents() {
        InboundEventHandler inboundHandler = new InboundEventHandler();

        CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler> handler =
                new CombinedChannelDuplexHandler<>(
                        inboundHandler, new ChannelHandler() { });

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
    public void testOutboundEvents() throws Exception {
        ChannelHandler inboundHandler = new ChannelHandlerAdapter() { };
        OutboundEventHandler outboundHandler = new OutboundEventHandler();

        CombinedChannelDuplexHandler<ChannelHandler, ChannelHandler> handler =
                new CombinedChannelDuplexHandler<>(
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

    private static void doOutboundOperations(Channel channel) throws Exception {
        channel.pipeline().bind(LOCAL_ADDRESS).asStage().sync();
        channel.pipeline().connect(REMOTE_ADDRESS, LOCAL_ADDRESS).asStage().sync();
        channel.pipeline().write(MSG).asStage().sync();
        channel.pipeline().flush();
        channel.pipeline().read();
        channel.pipeline().sendOutboundEvent(USER_EVENT);
        channel.pipeline().disconnect().asStage().sync();
        channel.pipeline().close().asStage().sync();
        channel.pipeline().deregister().asStage().sync();
    }

    private static void assertOutboundOperations(OutboundEventHandler outboundHandler) {
        assertEquals(Event.BIND, outboundHandler.pollEvent());
        assertEquals(Event.CONNECT, outboundHandler.pollEvent());
        assertEquals(Event.WRITE, outboundHandler.pollEvent());
        assertEquals(Event.FLUSH, outboundHandler.pollEvent());
        assertEquals(Event.READ, outboundHandler.pollEvent());
        assertEquals(Event.SEND_OUTBOUND_EVENT, outboundHandler.pollEvent());
        assertEquals(Event.CLOSE, outboundHandler.pollEvent());
        assertEquals(Event.CLOSE, outboundHandler.pollEvent());
        assertEquals(Event.DEREGISTER, outboundHandler.pollEvent());
    }

    private static void doInboundOperations(Channel channel) {
        channel.pipeline().fireChannelRegistered();
        channel.pipeline().fireChannelActive();
        channel.pipeline().fireChannelRead(MSG);
        channel.pipeline().fireChannelReadComplete();
        channel.pipeline().fireChannelExceptionCaught(CAUSE);
        channel.pipeline().fireChannelInboundEvent(USER_EVENT);
        channel.pipeline().fireChannelWritabilityChanged();
        channel.pipeline().fireChannelInactive();
        channel.pipeline().fireChannelUnregistered();
    }

    private static void assertInboundOperations(InboundEventHandler handler) {
        assertEquals(Event.REGISTERED, handler.pollEvent());
        assertEquals(Event.ACTIVE, handler.pollEvent());
        assertEquals(Event.CHANNEL_READ, handler.pollEvent());
        assertEquals(Event.CHANNEL_READ_COMPLETE, handler.pollEvent());
        assertEquals(Event.CHANNEL_EXCEPTION_CAUGHT, handler.pollEvent());
        assertEquals(Event.CHANNEL_INBOUND_EVENT, handler.pollEvent());
        assertEquals(Event.CHANNEL_WRITABILITY_CHANGED, handler.pollEvent());
        assertEquals(Event.INACTIVE, handler.pollEvent());
        assertEquals(Event.UNREGISTERED, handler.pollEvent());
    }

    private static final class InboundEventHandler implements ChannelHandler {
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
        public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) {
            queue.add(Event.CHANNEL_INBOUND_EVENT);
        }

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            queue.add(Event.CHANNEL_WRITABILITY_CHANGED);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            queue.add(Event.CHANNEL_EXCEPTION_CAUGHT);
        }

        Event pollEvent() {
            Object o = queue.poll();
            if (o instanceof AssertionError) {
                throw (AssertionError) o;
            }
            return (Event) o;
        }
    }

    private static final class OutboundEventHandler implements ChannelHandler {
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
        public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
            try {
                assertSame(LOCAL_ADDRESS, localAddress);
                queue.add(Event.BIND);
                return ctx.newSucceededFuture();
            } catch (AssertionError e) {
                return ctx.newFailedFuture(e);
            }
        }

        @Override
        public Future<Void> connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                            SocketAddress localAddress) {
            try {
                assertSame(REMOTE_ADDRESS, remoteAddress);
                assertSame(LOCAL_ADDRESS, localAddress);
                queue.add(Event.CONNECT);
                return ctx.newSucceededFuture();
            } catch (AssertionError e) {
                return ctx.newFailedFuture(e);
            }
        }

        @Override
        public Future<Void> disconnect(ChannelHandlerContext ctx) {
            queue.add(Event.DISCONNECT);
            return ctx.newSucceededFuture();
        }

        @Override
        public Future<Void> close(ChannelHandlerContext ctx) {
            queue.add(Event.CLOSE);
            return ctx.newSucceededFuture();
        }

        @Override
        public Future<Void> deregister(ChannelHandlerContext ctx) {
            queue.add(Event.DEREGISTER);
            return ctx.newSucceededFuture();
        }

        @Override
        public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
            queue.add(Event.READ);
        }

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            try {
                assertSame(MSG, msg);
                queue.add(Event.WRITE);
                return ctx.newSucceededFuture();
            } catch (AssertionError e) {
                return ctx.newFailedFuture(e);
            }
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            queue.add(Event.FLUSH);
        }

        @Override
        public Future<Void> sendOutboundEvent(ChannelHandlerContext ctx, Object event) {
            try {
                assertSame(USER_EVENT, event);
                queue.add(Event.SEND_OUTBOUND_EVENT);
                return ctx.newSucceededFuture();
            } catch (AssertionError e) {
                return ctx.newFailedFuture(e);
            }
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
