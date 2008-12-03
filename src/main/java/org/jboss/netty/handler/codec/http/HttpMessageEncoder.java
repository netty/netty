/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.handler.codec.http;

import static org.jboss.netty.channel.Channels.*;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.*;

import java.util.List;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * encodes an http message
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public abstract class HttpMessageEncoder extends SimpleChannelHandler {
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!(e.getMessage() instanceof HttpMessage)) {
            ctx.sendDownstream(e);
            return;
        }
        HttpMessage request = (HttpMessage) e.getMessage();
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        encodeInitialLine(buf, request);
        encodeHeaders(buf, request);
        buf.writeBytes(CRLF);
        if (request.getContent() != null) {
            buf.writeBytes(request.getContent());
        }
        write(ctx, e.getChannel(), e.getFuture(), buf, e.getRemoteAddress());
    }

    /**
     * writes the headers
     * Header1: value1
     * Header2: value2
     *
     * @param buf
     * @param message
     */
    public void encodeHeaders(ChannelBuffer buf, HttpMessage message) {
        Set<String> headers = message.getHeaderNames();
        for (String header : headers) {
            List<String> values = message.getHeaders(header);
            for (String value : values) {

                buf.writeBytes(header.getBytes());
                buf.writeByte(COLON);
                buf.writeByte(SP);
                buf.writeBytes(value.getBytes());
                buf.writeBytes(CRLF);
            }
        }
    }

    protected abstract void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception;
}
