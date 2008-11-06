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

import org.jboss.netty.buffer.ChannelBuffer;
import static org.jboss.netty.util.HttpCodecUtil.*;
import org.jboss.netty.util.UriBuilder;

import java.util.Set;
import java.util.List;

/**
 * encodes an http request
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpRequestEncoder extends HttpMessageEncoder {

    /**
     * writes the initial line i.e. 'GET /path/to/file/index.html HTTP/1.0'
     *
     * @param buf
     * @param message
     */
    public void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception {
        HttpRequest request = (HttpRequest) message;
        buf.writeBytes(request.getMethod().getMethod().getBytes());
        buf.writeByte(SP);
        UriBuilder uriBuilder = new UriBuilder(request.getPath());
        Set<String> paramNames = request.getParameterNames();
        for (String paramName : paramNames) {
            List<String> values = request.getParameters(paramName);
            for (String value : values) {
                uriBuilder.addParam(paramName, value);
            }
        }
        buf.writeBytes(uriBuilder.toUri().toASCIIString().getBytes());
        buf.writeByte(SP);
        buf.writeBytes(request.getProtocolVersion().getVersion().getBytes());
        buf.writeBytes(CRLF);
    }

}
