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
package org.jboss.netty.example.objectecho;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;

/**
 * Handles both client-side and server-side handler depending on which
 * constructor was called.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("all")
public class ObjectEchoHandler extends SimpleChannelUpstreamHandler {

    private static final Logger logger = Logger.getLogger(
            ObjectEchoHandler.class.getName());

    private final List<Integer> firstMessage;
    private final AtomicLong transferredMessages = new AtomicLong();

    /**
     * Creates a server-side handler.
     */
    public ObjectEchoHandler() {
        firstMessage = new ArrayList<Integer>();
    }

    /**
     * Creates a client-side handler.
     */
    public ObjectEchoHandler(int firstMessageSize) {
        if (firstMessageSize <= 0) {
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
        // Add encoder and decoder as soon as a new channel is created so that
        // a Java object is serialized and deserialized.
        e.getChannel().getPipeline().addFirst("encoder", new ObjectEncoder());
        e.getChannel().getPipeline().addFirst("decoder", new ObjectDecoder());
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Send the first message if this handler is a client-side handler.
        if (!firstMessage.isEmpty()) {
            e.getChannel().write(firstMessage);
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        // Echo back the received object to the client.
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
