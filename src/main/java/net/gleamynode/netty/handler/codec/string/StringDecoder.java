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

import java.nio.charset.Charset;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelUpstream;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;
import net.gleamynode.netty.pipeline.UpstreamHandler;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@PipelineCoverage("all")
public class StringDecoder implements UpstreamHandler<ChannelEvent> {

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
            PipeContext<ChannelEvent> context, ChannelEvent element) throws Exception {
        if (!(element instanceof MessageEvent)) {
            context.sendUpstream(element);
            return;
        }

        MessageEvent e = (MessageEvent) element;
        if (!(e.getMessage() instanceof ByteArray)) {
            context.sendUpstream(element);
            return;
        }

        ByteArray src = (ByteArray) e.getMessage();
        byte[] dst = new byte[src.length()];
        src.get(src.firstIndex(), dst);
        ChannelUpstream.fireMessageReceived(context, e.getChannel(), new String(dst, charsetName));
    }
}
