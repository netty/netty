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

import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * encodes an http message
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public abstract class HttpMessageEncoder extends OneToOneEncoder {

    private static final ChannelBuffer LAST_CHUNK = copiedBuffer("0\r\n\r\n", "ASCII");

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            HttpMessage request = (HttpMessage) msg;
            ChannelBuffer header = ChannelBuffers.dynamicBuffer(
                    channel.getConfig().getBufferFactory());
            encodeInitialLine(header, request);
            encodeHeaders(header, request);
            encodeCookies(header, request);
            header.writeBytes(CRLF);

            ChannelBuffer content = request.getContent();
            if (content == null) {
                return header; // no content
            } else {
                return wrappedBuffer(header, content);
            }
        }

        if (msg instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) msg;
            if (chunk.isLast()) {
                return LAST_CHUNK.duplicate();
            } else {
                ChannelBuffer content = chunk.getContent();
                int contentLength = content.readableBytes();

                return wrappedBuffer(
                        copiedBuffer(Integer.toHexString(contentLength), "ASCII"),
                        wrappedBuffer(CRLF),
                        content.slice(content.readerIndex(), contentLength),
                        wrappedBuffer(CRLF));
            }
        }

        // Unknown message type.
        return msg;
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

    public void encodeCookies(ChannelBuffer buf, HttpMessage message) {
        Collection<String> cookieNames = message.getCookieNames();
        if(cookieNames.isEmpty()) {
            return;
        }
        buf.writeBytes(getCookieHeaderName());
        buf.writeByte(COLON);
        buf.writeByte(SP);
        for (String cookieName : cookieNames) {
            buf.writeBytes(cookieName.getBytes());
            buf.writeByte(EQUALS);
            buf.writeBytes(message.getCookie(cookieName).getValue().getBytes());
            buf.writeByte(SEMICOLON);
        }
        buf.writeBytes(CRLF);
    }

    protected abstract byte[] getCookieHeaderName();
    protected abstract void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception;
}
