/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.example.factorial;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * Handler for a client-side channel.  Please note that this handler's
 * {@link ChannelPipelineCoverage} annotation value is "one".  It means
 * this handler maintains some stateful information which is specific to
 * a certain channel.  Therefore, an instance of this handler can
 * cover only one ChannelPipeline and Channel pair.  You have to create
 * a new handler instance whenever you create a new channel and insert
 * this handler to avoid a race condition.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one") // <-- HERE
public class FactorialClientHandler extends SimpleChannelHandler {

    private static final Logger logger = Logger.getLogger(
            FactorialClientHandler.class.getName());

    // Stateful properties
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
            ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        sendNumbers(e);
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) {
        sendNumbers(e);
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, final MessageEvent e) {
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
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }

    private void sendNumbers(ChannelStateEvent e) {
        Channel channel = e.getChannel();
        while (channel.isWritable()) {
            if (i <= count) {
                channel.write(Integer.valueOf(i));
                i ++;
            } else {
                break;
            }
        }
    }
}
