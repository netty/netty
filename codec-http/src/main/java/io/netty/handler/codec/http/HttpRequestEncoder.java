/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpConstants.SP;

/**
 * Encodes an {@link HttpRequest} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 */
public class HttpRequestEncoder extends HttpObjectEncoder<HttpRequest> {
    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';
    private static final int SLASH_AND_SPACE_SHORT = (SLASH << 8) | SP;
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM = (SP << 16) | SLASH_AND_SPACE_SHORT;

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && !(msg instanceof HttpResponse);
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpRequest request) throws Exception {
        ByteBufUtil.copy(request.method().asciiName(), buf);

        String uri = request.uri();

        if (uri.isEmpty()) {
            // Add " / " as absolute path if uri is not present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            ByteBufUtil.writeMediumBE(buf, SPACE_SLASH_AND_SPACE_MEDIUM);
        } else {
            CharSequence uriCharSequence = uri;
            boolean needSlash = false;
            int schemeEnd = uri.indexOf("://");
            if (schemeEnd != -1 && uri.charAt(0) != SLASH) {
                int authorityStart = schemeEnd + 3;
                // Correctly handle query params.
                // See https://github.com/netty/netty/issues/2732
                int index = uri.indexOf(QUESTION_MARK, authorityStart);
                if (index == -1) {
                    if (uri.lastIndexOf(SLASH) < authorityStart) {
                        // Preserve the server-wide absolute-form OPTIONS target (RFC 9112 section 3.2.4).
                        needSlash = !HttpMethod.OPTIONS.equals(request.method()) || !isHttpScheme(uri, schemeEnd);
                    }
                } else {
                    if (uri.lastIndexOf(SLASH, index) < authorityStart) {
                        uriCharSequence = new StringBuilder(uri).insert(index, SLASH);
                    }
                }
            }
            buf.writeByte(SP).writeCharSequence(uriCharSequence, CharsetUtil.UTF_8);
            if (needSlash) {
                // write "/ " after uri
                ByteBufUtil.writeShortBE(buf, SLASH_AND_SPACE_SHORT);
            } else {
                buf.writeByte(SP);
            }
        }

        request.protocolVersion().encode(buf);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }

    private static boolean isHttpScheme(String uri, int schemeEnd) {
        return schemeEnd == 4 && AsciiString.regionMatchesAscii(uri, true, 0, "http", 0, 4)
                || schemeEnd == 5 && AsciiString.regionMatchesAscii(uri, true, 0, "https", 0, 5);
    }
}
