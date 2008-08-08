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
package net.gleamynode.netty.example.discard;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;

@PipelineCoverage("all")
public class DiscardHandler extends ChannelEventHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            DiscardHandler.class.getName());

    private final Random random = new Random();
    private final int messageSize;
    private final AtomicLong transferredBytes = new AtomicLong();

    public DiscardHandler() {
        this(0);
    }

    public DiscardHandler(int messageSize) {
        if (messageSize < 0) {
            throw new IllegalArgumentException(
                    "messageSize: " + messageSize);
        }
        this.messageSize = messageSize;
    }

    public long getTransferredBytes() {
        return transferredBytes.get();
    }

    @Override
    public void handleUpstream(PipeContext<ChannelEvent> ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    protected void channelConnected(PipeContext<ChannelEvent> ctx, ChannelStateEvent e) {
        generateTraffic(e);
    }

    @Override
    protected void channelInterestChanged(PipeContext<ChannelEvent> ctx, ChannelStateEvent e) {
        generateTraffic(e);
    }

    @Override
    protected void messageReceived(PipeContext<ChannelEvent> ctx, MessageEvent e) {
        transferredBytes.addAndGet(((ByteArray) e.getMessage()).length());
    }

    @Override
    protected void exceptionCaught(PipeContext<ChannelEvent> ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }

    private void generateTraffic(ChannelStateEvent e) {
        Channel channel = e.getChannel();
        while (channel.isWritable()) {
            ByteArray m = nextMessage();
            if (m == null) {
                break;
            }
            channel.write(m);
        }
    }

    private ByteArray nextMessage() {
        if (messageSize == 0) {
            return null;
        }

        byte[] content = new byte[messageSize];
        random.nextBytes(content);
        return new HeapByteArray(content);
    }
}
