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
import io.netty.buffer.ByteBufUtil;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * Encodes an {@link HttpResponse} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 */
public class HttpResponseEncoder extends HttpObjectEncoder<HttpResponse> {

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return super.acceptOutboundMessage(msg) && !(msg instanceof HttpRequest);
    }

    @Override
    protected void encodeInitialLine(ByteBuf buf, HttpResponse response) throws Exception {
        response.protocolVersion().encode(buf);
        buf.writeByte(SP);
        response.status().encode(buf);
        ByteBufUtil.writeShortBE(buf, CRLF_SHORT);
    }

    @Override
    protected void sanitizeHeadersBeforeEncode(HttpResponse msg, boolean isAlwaysEmpty) {
        if (isAlwaysEmpty) {
            HttpResponseStatus status = msg.status();
            if (status.codeClass() == HttpStatusClass.INFORMATIONAL ||
                    status.code() == HttpResponseStatus.NO_CONTENT.code()) {

                // Stripping Content-Length:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.2
                msg.headers().remove(HttpHeaderNames.CONTENT_LENGTH);

                // Stripping Transfer-Encoding:
                // See https://tools.ietf.org/html/rfc7230#section-3.3.1
                msg.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
            }
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpResponse msg) {
        // Correctly handle special cases as stated in:
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        HttpResponseStatus status = msg.status();

        if (status.codeClass() == HttpStatusClass.INFORMATIONAL) {

            if (status.code() == HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
                // We need special handling for WebSockets version 00 as it will include an body.
                // Fortunally this version should not really be used in the wild very often.
                // See https://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-00#section-1.2
                return msg.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
            }
            return true;
        }
        return status.code() == HttpResponseStatus.NO_CONTENT.code() ||
                status.code() == HttpResponseStatus.NOT_MODIFIED.code();
    }
}
