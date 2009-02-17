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

import static org.jboss.netty.handler.codec.http.HttpCodecUtil.*;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * encodes an http request
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class HttpRequestEncoder extends HttpMessageEncoder {
    private static final byte[] COOKIE_HEADER = "Cookie".getBytes();
    /**
     * writes the initial line i.e. 'GET /path/to/file/index.html HTTP/1.0'
     */
    @Override
    protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception {
        HttpRequest request = (HttpRequest) message;
        buf.writeBytes(request.getMethod().toString().getBytes());
        buf.writeByte(SP);
        buf.writeBytes(request.getUri().getBytes());
        buf.writeByte(SP);
        buf.writeBytes(request.getProtocolVersion().toString().getBytes());
        buf.writeBytes(CRLF);
    }

    @Override
    public byte[] getCookieHeaderName() {
        return COOKIE_HEADER;
    }
}
