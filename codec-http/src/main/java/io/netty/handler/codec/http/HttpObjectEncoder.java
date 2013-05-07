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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

import java.util.Map;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * Encodes an {@link HttpMessage} or an {@link HttpContent} into
 * a {@link ByteBuf}.
 *
 * <h3>Extensibility</h3>
 *
 * Please note that this encoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the encoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 */
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToByteEncoder<HttpObject> {

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, ByteBuf out) throws Exception {
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + msg.getClass().getSimpleName());
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;

            // Encode the message.
            encodeInitialLine(out, m);
            encodeHeaders(out, m);
            out.writeByte(CR);
            out.writeByte(LF);

            state = HttpHeaders.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
        }

        if (msg instanceof HttpContent) {
            if (state == ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + msg.getClass().getSimpleName());
            }

            HttpContent chunk = (HttpContent) msg;
            ByteBuf content = chunk.content();
            int contentLength = content.readableBytes();

            if (state == ST_CONTENT_NON_CHUNK) {
                if (contentLength > 0) {
                    out.writeBytes(content, content.readerIndex(), content.readableBytes());
                }

                if (chunk instanceof LastHttpContent) {
                    state = ST_INIT;
                }
            } else if (state == ST_CONTENT_CHUNK) {
                if (contentLength > 0) {
                    out.writeBytes(copiedBuffer(Integer.toHexString(contentLength), CharsetUtil.US_ASCII));
                    out.writeByte(CR);
                    out.writeByte(LF);
                    out.writeBytes(content, content.readerIndex(), contentLength);
                    out.writeByte(CR);
                    out.writeByte(LF);
                }

                if (chunk instanceof LastHttpContent) {
                    out.writeByte((byte) '0');
                    out.writeByte(CR);
                    out.writeByte(LF);
                    encodeTrailingHeaders(out, (LastHttpContent) chunk);
                    out.writeByte(CR);
                    out.writeByte(LF);
                    state = ST_INIT;
                }
            } else {
                throw new Error();
            }
        }
    }

    private static void encodeHeaders(ByteBuf buf, HttpMessage message) {
        for (Map.Entry<String, String> h: message.headers()) {
            encodeHeader(buf, h.getKey(), h.getValue());
        }
    }

    private static void encodeTrailingHeaders(ByteBuf buf, LastHttpContent trailer) {
        for (Map.Entry<String, String> h: trailer.trailingHeaders()) {
            encodeHeader(buf, h.getKey(), h.getValue());
        }
    }

    private static void encodeHeader(ByteBuf buf, String header, String value) {
        buf.writeBytes(header.getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(COLON);
        buf.writeByte(SP);
        buf.writeBytes(value.getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }

    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;
}
