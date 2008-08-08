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
package net.gleamynode.netty.handler.codec.string;

import static net.gleamynode.netty.channel.Channels.*;

import java.nio.charset.Charset;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.channel.ChannelPipelineCoverage;
import net.gleamynode.netty.channel.ChannelUpstreamHandler;
import net.gleamynode.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@ChannelPipelineCoverage("all")
public class StringDecoder implements ChannelUpstreamHandler {

    private final String charsetName;

    public StringDecoder() {
        this(Charset.defaultCharset());
    }

    public StringDecoder(String charsetName) {
        this(Charset.forName(charsetName));
    }

    public StringDecoder(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        charsetName = charset.name();
    }

    public void handleUpstream(
            ChannelHandlerContext context, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            context.sendUpstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        if (!(e.getMessage() instanceof ChannelBuffer)) {
            context.sendUpstream(evt);
            return;
        }

        ChannelBuffer src = (ChannelBuffer) e.getMessage();
        byte[] dst = new byte[src.readableBytes()];
        src.getBytes(src.readerIndex(), dst);
        fireMessageReceived(context, e.getChannel(), new String(dst, charsetName));
    }
}
