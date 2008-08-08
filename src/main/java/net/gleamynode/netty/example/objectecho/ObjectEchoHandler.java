/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.example.objectecho;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.ChannelState;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.channel.SimpleChannelHandler;
import net.gleamynode.netty.handler.codec.serialization.ObjectDecoder;
import net.gleamynode.netty.handler.codec.serialization.ObjectEncoder;

@ChannelPipelineCoverage("all")
public class ObjectEchoHandler extends SimpleChannelHandler {

    private static final Logger logger = Logger.getLogger(
            ObjectEchoHandler.class.getName());

    private final List<Integer> firstMessage;
    private final AtomicLong transferredMessages = new AtomicLong();

    public ObjectEchoHandler() {
        this(0);
    }

    public ObjectEchoHandler(int firstMessageSize) {
        if (firstMessageSize < 0) {
            throw new IllegalArgumentException(
                    "firstMessageSize: " + firstMessageSize);
        }
        firstMessage = new ArrayList<Integer>(firstMessageSize);
        for (int i = 0; i < firstMessageSize; i ++) {
            firstMessage.add(Integer.valueOf(i));
        }
    }

    public long getTransferredMessages() {
        return transferredMessages.get();
    }

    @Override
    public void handleUpstream(
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent &&
            ((ChannelStateEvent) e).getState() != ChannelState.INTEREST_OPS) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx,
            ChannelStateEvent e) throws Exception {
        e.getChannel().getPipeline().addFirst("encoder", new ObjectEncoder());
        e.getChannel().getPipeline().addFirst("decoder", new ObjectDecoder());
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        if (!firstMessage.isEmpty()) {
            e.getChannel().write(firstMessage);
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        transferredMessages.incrementAndGet();
        e.getChannel().write(e.getMessage());
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }
}
