/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * The default {@link HttpChunkTrailer} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class DefaultHttpChunkTrailer implements HttpChunkTrailer {

    private final HttpHeaders headers = new HttpHeaders() {
        @Override
        void validateHeaderName(String name) {
            super.validateHeaderName(name);
            if (name.equalsIgnoreCase(HttpHeaders.Names.CONTENT_LENGTH) ||
                name.equalsIgnoreCase(HttpHeaders.Names.TRANSFER_ENCODING) ||
                name.equalsIgnoreCase(HttpHeaders.Names.TRAILER)) {
                throw new IllegalArgumentException(
                        "prohibited trailing header: " + name);
            }
        }
    };

    public boolean isLast() {
        return true;
    }

    public void addHeader(final String name, final Object value) {
        headers.addHeader(name, value);
    }

    public void setHeader(final String name, final Object value) {
        headers.setHeader(name, value);
    }

    public void setHeader(final String name, final Iterable<?> values) {
        headers.setHeader(name, values);
    }

    public void removeHeader(final String name) {
        headers.removeHeader(name);
    }

    public void clearHeaders() {
        headers.clearHeaders();
    }

    public String getHeader(final String name) {
        return headers.getHeader(name);
    }

    public List<String> getHeaders(final String name) {
        return headers.getHeaders(name);
    }

    public List<Map.Entry<String, String>> getHeaders() {
        return headers.getHeaders();
    }

    public boolean containsHeader(final String name) {
        return headers.containsHeader(name);
    }

    public Set<String> getHeaderNames() {
        return headers.getHeaderNames();
    }

    public ChannelBuffer getContent() {
        return ChannelBuffers.EMPTY_BUFFER;
    }

    public void setContent(ChannelBuffer content) {
        throw new IllegalStateException("read-only");
    }
}
