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
package net.gleamynode.netty.handler.codec.serialization;

import static net.gleamynode.netty.buffer.ChannelBuffers.*;
import static net.gleamynode.netty.channel.Channels.*;

import java.io.ObjectOutputStream;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBufferOutputStream;
import net.gleamynode.netty.channel.ChannelDownstreamHandler;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@ChannelPipelineCoverage("all")
public class ObjectEncoder implements ChannelDownstreamHandler {
    private static final byte[] LENGTH_PLACEHOLDER = new byte[4];

    private final int estimatedLength;

    public ObjectEncoder() {
        this(512);
    }

    public ObjectEncoder(int estimatedLength) {
        if (estimatedLength <= 0) {
            throw new IllegalArgumentException(
                    "estimatedLength: " + estimatedLength);
        }
        this.estimatedLength = estimatedLength;
    }

    public void handleDownstream(
            ChannelHandlerContext context, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            context.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        ChannelBufferOutputStream bout =
            new ChannelBufferOutputStream(dynamicBuffer(estimatedLength));
        bout.write(LENGTH_PLACEHOLDER);
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(e.getMessage());
        oout.flush();
        oout.close();

        ChannelBuffer msg = bout.buffer();
        msg.setInt(0, msg.writerIndex() - 4);

        write(context, e.getChannel(), e.getFuture(), msg, e.getRemoteAddress());
    }
}
