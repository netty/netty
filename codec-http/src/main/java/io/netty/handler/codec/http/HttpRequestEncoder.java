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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * Encodes an {@link HttpHeaders} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 */
public class HttpRequestEncoder extends HttpObjectEncoder {
    private static final char SLASH = '/';

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        if (!super.acceptOutboundMessage(msg)) {
            return false;
        }

        if (!(msg instanceof HttpHeaders)) {
            return true;
        }

        return ((HttpHeaders) msg).isRequest();
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpHeaders request) throws Exception {
        buf.writeBytes(request.getMethod().toString().getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(SP);

        // Add / as absolute path if no is present.
        // See http://tools.ietf.org/html/rfc2616#section-5.1.2
        String uri = request.getPath();
        int start = uri.indexOf("://");
        if (start != -1) {
            int startIndex = start + 3;
            if (uri.lastIndexOf(SLASH) <= startIndex) {
                uri += SLASH;
            }
        }
        buf.writeBytes(uri.getBytes("UTF-8"));

        buf.writeByte(SP);
        buf.writeBytes(request.getVersion().toString().getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }
}
