/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.rtsp;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.UnsupportedMessageTypeException;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObjectEncoder;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.util.internal.StringUtil;

import static io.netty5.handler.codec.http.HttpConstants.CR;
import static io.netty5.handler.codec.http.HttpConstants.LF;
import static io.netty5.handler.codec.http.HttpConstants.SP;
import static io.netty5.util.CharsetUtil.US_ASCII;
import static io.netty5.util.CharsetUtil.UTF_8;

/**
 * Encodes an RTSP message represented in {@link HttpMessage} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 */
public class RtspEncoder extends HttpObjectEncoder<HttpMessage> {
    private static final short CRLF_SHORT = (CR << 8) | LF;

    @Override
    public boolean acceptOutboundMessage(final Object msg)
           throws Exception {
        return super.acceptOutboundMessage(msg) && ((msg instanceof HttpRequest) || (msg instanceof HttpResponse));
    }

    @Override
    protected void encodeInitialLine(final Buffer buf, final HttpMessage message) throws Exception {
        if (message instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) message;
            buf.writeCharSequence(request.method().asciiName(), US_ASCII);
            buf.writeByte(SP);
            buf.writeCharSequence(request.uri(), UTF_8);
            buf.writeByte(SP);
            buf.writeCharSequence(request.protocolVersion().toString(), US_ASCII);
            buf.writeShort(CRLF_SHORT);
        } else if (message instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) message;
            buf.writeCharSequence(response.protocolVersion().toString(), US_ASCII);
            buf.writeByte(SP);
            buf.writeCharSequence(response.status().codeAsText(), US_ASCII);
            buf.writeByte(SP);
            buf.writeCharSequence(response.status().reasonPhrase(), US_ASCII);
            buf.writeShort(CRLF_SHORT);
        } else {
            throw new UnsupportedMessageTypeException("Unsupported type " + StringUtil.simpleClassName(message));
        }
    }
}
