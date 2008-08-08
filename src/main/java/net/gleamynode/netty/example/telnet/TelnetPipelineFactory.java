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

import net.gleamynode.netty.channel.ChannelHandler;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelPipelineFactory;
import net.gleamynode.netty.channel.DefaultChannelPipeline;
import net.gleamynode.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import net.gleamynode.netty.handler.codec.frame.Delimiters;
import net.gleamynode.netty.handler.codec.string.StringDecoder;
import net.gleamynode.netty.handler.codec.string.StringEncoder;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class TelnetPipelineFactory implements
        ChannelPipelineFactory {

    private final ChannelHandler handler;

    public TelnetPipelineFactory(ChannelHandler handler) {
        this.handler = handler;
    }

    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = new DefaultChannelPipeline();

        // Add the text line codec first,
        pipeline.addLast("framer", new DelimiterBasedFrameDecoder(
                8192, Delimiters.newLineDelimiter()));
        pipeline.addLast("decoder", new StringDecoder());
        pipeline.addLast("encoder", new StringEncoder());

        // and then business logic.
        pipeline.addLast("handler", handler);

        return pipeline;
    }
}
