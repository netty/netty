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

import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.handler.codec.frame.DelimiterBasedFrameHandler;
import net.gleamynode.netty.handler.codec.frame.Delimiters;
import net.gleamynode.netty.handler.codec.string.StringDecoder;
import net.gleamynode.netty.handler.codec.string.StringEncoder;
import net.gleamynode.netty.pipeline.PipeContext;

public class TelnetClientHandler extends ChannelEventHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            TelnetClientHandler.class.getName());

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
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) {
        System.err.println(e.getMessage());
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
