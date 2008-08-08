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

import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.ChannelDownstream;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.DownstreamHandler;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@PipelineCoverage("all")
public class StringEncoder implements DownstreamHandler<ChannelEvent> {

    private final String charsetName;

    public StringEncoder() {
        this(Charset.defaultCharset());
    }

    public StringEncoder(String charsetName) {
        this(Charset.forName(charsetName));
    }

    public StringEncoder(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        charsetName = charset.name();
    }

    public void handleDownstream(
            PipeContext<ChannelEvent> context, ChannelEvent element) throws Exception {
        if (!(element instanceof MessageEvent)) {
            context.sendDownstream(element);
            return;
        }

        MessageEvent e = (MessageEvent) element;
        if (!(e.getMessage() instanceof String)) {
            context.sendDownstream(element);
            return;
        }

        String src = (String) e.getMessage();
        ChannelDownstream.write(
                context, e.getChannel(), e.getFuture(), new HeapByteArray(src.getBytes(charsetName)));
    }
}
