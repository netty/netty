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
package net.gleamynode.netty.example.factorial;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;

public class FactorialClientHandler extends ChannelEventHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            FactorialClientHandler.class.getName());

    private int i = 1;
    private int receivedMessages = 0;
    private final int count;
    final BlockingQueue<BigInteger> answer = new LinkedBlockingQueue<BigInteger>();

    public FactorialClientHandler(int count) {
        this.count = count;
    }

    public BigInteger getFactorial() {
        for (;;) {
            try {
                return answer.take();
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    @Override
    public void handleUpstream(
            PipeContext<ChannelEvent> ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    protected void channelOpen(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) {
        // FIXME better pipeline initialization because this handler is stateful.
        e.getChannel().getPipeline().addFirst(
                "decoder", new BigIntegerDecoder());
        e.getChannel().getPipeline().addAfter(
                "decoder", "encoder", new NumberEncoder());
    }

    @Override
    protected void channelConnected(PipeContext<ChannelEvent> ctx, ChannelStateEvent e) {
        sendNumbers(e);
    }

    @Override
    protected void channelInterestChanged(PipeContext<ChannelEvent> ctx, ChannelStateEvent e) {
        sendNumbers(e);
    }

    @Override
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, final MessageEvent e) {
        receivedMessages ++;
        if (receivedMessages == count) {
            // Offer the answer after closing the connection.
            e.getChannel().close().addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    answer.offer((BigInteger) e.getMessage());
                }
            });
        }
    }

    @Override
    protected void exceptionCaught(
            PipeContext<ChannelEvent> ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }

    private void sendNumbers(ChannelStateEvent e) {
        Channel channel = e.getChannel();
        while ((channel.getInterestOps() & Channel.OP_WRITE) == 0) {
            if (i <= count) {
                channel.write(Integer.valueOf(i));
                i ++;
            } else {
                break;
            }
        }
    }
}
