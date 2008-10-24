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

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * a default Http Message which holds the headers and body.
 *
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class HttpMessageImpl implements HttpMessage {
    Map<String, String> headers = new HashMap<String, String>();

    private ChannelBuffer content;

    final HttpProtocol httpProtocol;

    public HttpMessageImpl(HttpProtocol httpProtocol) {
        this.httpProtocol = httpProtocol;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public boolean removeHeader(String key) {
        return headers.remove(key) != null;
    }

    public int getContentLength() {
        String contentLength = headers.get(HttpHeaders.CONTENT_LENGTH);
        if (contentLength != null) {
            return Integer.valueOf(contentLength);
        }
        return 0;
    }

    public boolean isChunked() {
        String chunked = headers.get(HttpHeaders.TRANSFER_ENCODING.KEY);
        return chunked != null && chunked.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING.CHUNKED);
    }

    public void setContent(ChannelBuffer content) {
        this.content = content;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    public Set<String> getHeaders() {
        return headers.keySet();
    }

    public HttpProtocol getProtocol() {
        return httpProtocol;
    }

    public ChannelBuffer getContent() {
        return content;
    }
}
