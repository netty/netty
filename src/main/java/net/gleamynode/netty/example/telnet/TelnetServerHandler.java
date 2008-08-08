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
package net.gleamynode.netty.example.telnet;

import java.net.InetAddress;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.handler.codec.frame.DelimiterBasedFrameHandler;
import net.gleamynode.netty.handler.codec.frame.Delimiters;
import net.gleamynode.netty.handler.codec.string.StringDecoder;
import net.gleamynode.netty.handler.codec.string.StringEncoder;
import net.gleamynode.netty.pipeline.PipeContext;

public class TelnetServerHandler extends ChannelEventHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            TelnetServerHandler.class.getName());

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
        e.getChannel().getPipeline().addFirst(
                "framer", new DelimiterBasedFrameHandler(
                        8192, Delimiters.newLineDelimiter()));
        e.getChannel().getPipeline().addAfter(
                "framer", "decoder", new StringDecoder());
        e.getChannel().getPipeline().addAfter(
                "framer", "encoder", new StringEncoder());
    }

    @Override
    protected void channelConnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        e.getChannel().write(
                new HeapByteArray(
                        ("Welcome to " +
                                InetAddress.getLocalHost().getHostName() +
                                "!\n").getBytes()));
        e.getChannel().write(
                new HeapByteArray(
                        ("It's " + new Date() + " now.\n").getBytes()));
    }

    @Override
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) {

        // Convert to a String first.
        String request = (String) e.getMessage();

        // Generate and write a response.
        String response;
        boolean close = false;
        if (request.isEmpty()) {
            response = "Please type something.\n";
        } else if (request.toLowerCase().equals("bye")) {
            response = "Have a good day!\n";
            close = true;
        } else {
            response = "Did you say '" + request + "'?\n";
        }

        ChannelFuture future = e.getChannel().write(response);

        // Close the connection after sending 'Have a good day!'
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
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
}
