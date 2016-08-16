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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.LockSupport;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Channel handler that allows to easily access inbound messages.
 */
public class LastInboundHandler extends ChannelDuplexHandler {
    private final Queue<Object> inboundMessages = new ArrayDeque<Object>();
    private final Queue<Object> userEvents = new ArrayDeque<Object>();
    private final Queue<Object> inboundMessagesAndUserEvents = new ArrayDeque<Object>();
    private Throwable lastException;
    private ChannelHandlerContext ctx;
    private boolean channelActive;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!channelActive) {
            throw new IllegalStateException("channelInactive may only be fired once after channelActive.");
        }
        channelActive = false;
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        inboundMessages.add(msg);
        inboundMessagesAndUserEvents.add(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        userEvents.add(evt);
        inboundMessagesAndUserEvents.add(evt);
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
        T message = (T) inboundMessages.poll();
        if (message == inboundMessagesAndUserEvents.peek()) {
            inboundMessagesAndUserEvents.poll();
        }
        return message;
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
        T message = (T) userEvents.poll();
        if (message == inboundMessagesAndUserEvents.peek()) {
            inboundMessagesAndUserEvents.poll();
        }
        return message;
    }

    /**
     * Useful to test order of events and messages.
     */
    @SuppressWarnings("unchecked")
    public <T> T readInboundMessagesAndEvents() {
        T message = (T) inboundMessagesAndUserEvents.poll();

        if (message == inboundMessages.peek()) {
            inboundMessages.poll();
        } else if (message == userEvents.peek()) {
            userEvents.poll();
        }

        return message;
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
        while ((o = readInboundMessagesAndEvents()) != null) {
            ReferenceCountUtil.release(o);
        }
    }

    public Channel channel() {
        return ctx.channel();
    }
}
