/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;

import static org.jboss.netty.handler.codec.http.HttpConstants.*;

/**
 * Encodes an {@link HttpRequest} or an {@link HttpChunk} into
 * a {@link ChannelBuffer}.
 */
public class HttpRequestEncoder extends HttpMessageEncoder {
    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';

    @Override
    protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception {
        HttpRequest request = (HttpRequest) message;
        buf.writeBytes(request.getMethod().toString().getBytes("ASCII"));
        buf.writeByte(SP);

        // Add / as absolute path if no is present.
        // See http://tools.ietf.org/html/rfc2616#section-5.1.2
        String uri = request.getUri();
        int start = uri.indexOf("://");
        if (start != -1) {
            int startIndex = start + 3;
            // Correctly handle query params.
            // See https://github.com/netty/netty/issues/2732
            int index = uri.indexOf(QUESTION_MARK, startIndex);
            if (index == -1) {
                if (uri.lastIndexOf(SLASH) <= startIndex) {
                    uri += SLASH;
                }
            } else {
                if (uri.lastIndexOf(SLASH, index) <= startIndex) {
                    int len = uri.length();
                    StringBuilder sb = new StringBuilder(len + 1);
                    sb.append(uri, 0, index);
                    sb.append(SLASH);
                    sb.append(uri, index, len);
                    uri = sb.toString();
                }
            }
        }

        buf.writeBytes(uri.getBytes("UTF-8"));
        buf.writeByte(SP);
        buf.writeBytes(request.getProtocolVersion().toString().getBytes("ASCII"));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }
}
