/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Channel handler that allows to easily access inbound messages.
 */
public class LastInboundHandler extends ChannelDuplexHandler {
    private final List<Object> queue = new ArrayList<Object>();
    private final Consumer<ChannelHandlerContext> channelReadCompleteConsumer;
    private Throwable lastException;
    private ChannelHandlerContext ctx;
    private boolean channelActive;
    private String writabilityStates = "";

    // TODO(scott): use JDK 8's Consumer
    public interface Consumer<T> {
        void accept(T obj);
    }

    private static final Consumer<Object> NOOP_CONSUMER = new Consumer<Object>() {
        @Override
        public void accept(Object obj) {
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> noopConsumer() {
        return (Consumer<T>) NOOP_CONSUMER;
    }

    public LastInboundHandler() {
        this(LastInboundHandler.<ChannelHandlerContext>noopConsumer());
    }

    public LastInboundHandler(Consumer<ChannelHandlerContext> channelReadCompleteConsumer) {
        this.channelReadCompleteConsumer = checkNotNull(channelReadCompleteConsumer, "channelReadCompleteConsumer");
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (channelActive) {
            throw new IllegalStateException("channelActive may only be fired once.");
        }
        channelActive = true;
        super.channelActive(ctx);
    }

    public boolean isChannelActive() {
        return channelActive;
    }

    public String writabilityStates() {
        return writabilityStates;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!channelActive) {
            throw new IllegalStateException("channelInactive may only be fired once after channelActive.");
        }
        channelActive = false;
        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (writabilityStates == "") {
            writabilityStates = String.valueOf(ctx.channel().isWritable());
        } else {
            writabilityStates += "," + ctx.channel().isWritable();
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        queue.add(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        channelReadCompleteConsumer.accept(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        queue.add(new UserEvent(evt));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (lastException != null) {
            cause.printStackTrace();
        } else {
            lastException = cause;
        }
    }

    public void checkException() throws Exception {
        if (lastException == null) {
            return;
        }
        Throwable t = lastException;
        lastException = null;
        PlatformDependent.throwException(t);
    }

    @SuppressWarnings("unchecked")
    public <T> T readInbound() {
        for (int i = 0; i < queue.size(); i++) {
            Object o = queue.get(i);
            if (!(o instanceof UserEvent)) {
                queue.remove(i);
                return (T) o;
            }
        }

        return null;
    }

    public <T> T blockingReadInbound() {
        T msg;
        while ((msg = readInbound()) == null) {
            LockSupport.parkNanos(MILLISECONDS.toNanos(10));
        }
        return msg;
    }

    @SuppressWarnings("unchecked")
    public <T> T readUserEvent() {
        for (int i = 0; i < queue.size(); i++) {
            Object o = queue.get(i);
            if (o instanceof UserEvent) {
                queue.remove(i);
                return (T) ((UserEvent) o).evt;
            }
        }

        return null;
    }

    /**
     * Useful to test order of events and messages.
     */
    @SuppressWarnings("unchecked")
    public <T> T readInboundMessageOrUserEvent() {
        if (queue.isEmpty()) {
            return null;
        }
        Object o = queue.remove(0);
        if (o instanceof UserEvent) {
            return (T) ((UserEvent) o).evt;
        }
        return (T) o;
    }

    public void writeOutbound(Object... msgs) throws Exception {
        for (Object msg : msgs) {
            ctx.write(msg);
        }
        ctx.flush();
        EmbeddedChannel ch = (EmbeddedChannel) ctx.channel();
        ch.runPendingTasks();
        ch.checkException();
        checkException();
    }

    public void finishAndReleaseAll() throws Exception {
        checkException();
        Object o;
        while ((o = readInboundMessageOrUserEvent()) != null) {
            ReferenceCountUtil.release(o);
        }
    }

    public Channel channel() {
        return ctx.channel();
    }

    private static final class UserEvent {
        private final Object evt;

        UserEvent(Object evt) {
            this.evt = evt;
        }
    }
}
