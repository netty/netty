/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.security.core.Action;
import io.netty.security.core.Filter;
import io.netty.security.core.FiveTuple;
import io.netty.security.core.Util;
import io.netty.security.core.events.ChannelClosedEvent;
import io.netty.security.core.events.ObjectDroppedAndChannelClosedEvent;
import io.netty.security.core.events.ObjectDroppedEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a standard network handler which performs network filtering.
 * This handler instance is thread-safe and should be shared among different channels.
 */
@ChannelHandler.Sharable
public class StandardNetworkHandler extends ChannelInboundHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(StandardNetworkHandler.class);
    private final Map<Channel, FiveTuple> CHANNEL_TUPLE_MAP = new ConcurrentHashMap<>();

    private final Filter filter;

    /**
     * Create a new {@link StandardNetworkHandler} instance
     *
     * @param filter {@link Filter} implementation to use
     */
    public StandardNetworkHandler(Filter filter) {
        this.filter = ObjectUtil.checkNotNull(filter, "Filter");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (ctx.channel() instanceof SocketChannel) {
            channelActive0(ctx, fiveTupleSocketChannel((SocketChannel) ctx.channel()));
        } else if (ctx.channel() instanceof DatagramChannel) {
            DatagramChannel channel = (DatagramChannel) ctx.channel();

            // If Channel is connected then we can retrieve FiveTuple information
            // in just one go. This is a Client implementation, not Server.
            if (channel.isConnected()) {
                FiveTuple fiveTuple = Util.generateFiveTupleFrom(channel);
                channelActive0(ctx, fiveTuple);
            } else {
                // Channel is not connected, it means we have to generate FiveTuple
                // everytime a DatagramPacket arrives.
                ctx.fireChannelActive();
            }
        } else {
            logger.error("Unknown Channel Type: " + ctx.channel().getClass().getSimpleName());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        CHANNEL_TUPLE_MAP.remove(ctx.channel());
        ctx.fireChannelInactive();
    }

    private void channelActive0(ChannelHandlerContext ctx, FiveTuple fiveTuple) {
        // Process the connection
        Action action = filter.validateChannelActive(fiveTuple);
        if (action == Action.ACCEPT) {
            // Pass the event to next Handler in Pipeline
            ctx.fireChannelActive();
        } else if (action == Action.REJECT || action == Action.DROP) {
            ctx.channel().close();
            ctx.fireUserEventTriggered(ChannelClosedEvent.INSTANCE);
        } else {
            throw new IllegalArgumentException("Invalid Action: " + action);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        FiveTuple fiveTuple;
        if (ctx.channel() instanceof SocketChannel) {
            fiveTuple = fiveTupleSocketChannel((SocketChannel) ctx.channel());
        } else if (ctx.channel() instanceof DatagramChannel) {
            DatagramChannel datagramChannel = (DatagramChannel) ctx.channel();
            if (datagramChannel.isConnected()) {
                fiveTuple = fiveTupleDatagramChannel((DatagramChannel) ctx.channel());
            } else {
                fiveTuple = Util.generateFiveTupleFrom((DatagramPacket) msg);
            }
        } else {
            throw new IllegalArgumentException("Channel not supported: " + ctx.channel());
        }

        Action action = filter.validateObject(msg, fiveTuple);
        switch (action) {
            case ACCEPT:
                ctx.fireChannelRead(msg);
                break;
            case DROP:
                ReferenceCountUtil.release(msg);
                ctx.fireUserEventTriggered(ObjectDroppedEvent.INSTANCE);
                break;
            case REJECT:
                ReferenceCountUtil.release(msg);
                ctx.channel().close();
                ctx.fireUserEventTriggered(ObjectDroppedAndChannelClosedEvent.INSTANCE);
                break;
            default:
                throw new IllegalArgumentException("Invalid Action: " + action);
        }
    }

    private FiveTuple fiveTupleSocketChannel(SocketChannel channel) {
        FiveTuple fiveTuple = CHANNEL_TUPLE_MAP.get(channel);
        if (fiveTuple == null) {
            CHANNEL_TUPLE_MAP.put(channel, Util.generateFiveTupleFrom(channel));
        }
        return fiveTuple;
    }

    private FiveTuple fiveTupleDatagramChannel(DatagramChannel channel) {
        FiveTuple fiveTuple = CHANNEL_TUPLE_MAP.get(channel);
        if (fiveTuple == null) {
            CHANNEL_TUPLE_MAP.put(channel, Util.generateFiveTupleFrom(channel));
        }
        return fiveTuple;
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
