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
import io.netty.channel.FileRegion;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

import java.util.List;
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
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToMessageEncoder<Object> {
    private static final byte[] CRLF = { CR, LF };
    private static final byte[] ZERO_CRLF = { '0', CR, LF };
    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };
    private static final byte[] HEADER_SEPARATOR = { COLON, SP };
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(CRLF.length).writeBytes(CRLF));
    private static final ByteBuf ZERO_CRLF_CRLF_BUF = unreleasableBuffer(directBuffer(ZERO_CRLF_CRLF.length)
            .writeBytes(ZERO_CRLF_CRLF));

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;

            ByteBuf buf = ctx.alloc().buffer();
            // Encode the message.
            encodeInitialLine(buf, m);
            encodeHeaders(buf, m.headers());
            buf.writeBytes(CRLF);
            out.add(buf);
            state = HttpHeaders.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
        }
        if (msg instanceof HttpContent || msg instanceof ByteBuf || msg instanceof FileRegion) {
            if (state == ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            int contentLength = contentLength(msg);
            if (state == ST_CONTENT_NON_CHUNK) {
                if (contentLength > 0) {
                    out.add(encodeAndRetain(msg));
                } else {
                    // Need to produce some output otherwise an
                    // IllegalStateException will be thrown
                    out.add(EMPTY_BUFFER);
                }

                if (msg instanceof LastHttpContent) {
                    state = ST_INIT;
                }
            } else if (state == ST_CONTENT_CHUNK) {
                encodeChunkedContent(ctx, msg, contentLength, out);
            } else {
                throw new Error();
            }
        }
    }

    private void encodeChunkedContent(ChannelHandlerContext ctx, Object msg, int contentLength, List<Object> out) {
        if (contentLength > 0) {
            byte[] length = Integer.toHexString(contentLength).getBytes(CharsetUtil.US_ASCII);
            ByteBuf buf = ctx.alloc().buffer(length.length + 2);
            buf.writeBytes(length);
            buf.writeBytes(CRLF);
            out.add(buf);
            out.add(encodeAndRetain(msg));
            out.add(CRLF_BUF.duplicate());
        }

        if (msg instanceof LastHttpContent) {
            HttpHeaders headers = ((LastHttpContent) msg).trailingHeaders();
            if (headers.isEmpty()) {
                out.add(ZERO_CRLF_CRLF_BUF.duplicate());
            } else {
                ByteBuf buf = ctx.alloc().buffer();
                buf.writeBytes(ZERO_CRLF);
                encodeHeaders(buf, headers);
                buf.writeBytes(CRLF);
                out.add(buf);
            }

            state = ST_INIT;
        } else {
            if (contentLength == 0) {
                // Need to produce some output otherwise an
                // IllegalstateException will be thrown
                out.add(EMPTY_BUFFER);
            }
        }
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpObject || msg instanceof ByteBuf || msg instanceof FileRegion;
    }

    private static Object encodeAndRetain(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).retain();
        }
        if (msg instanceof HttpContent) {
            return ((HttpContent) msg).content().retain();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).retain();
        }
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    private static int contentLength(Object msg) {
        if (msg instanceof HttpContent) {
            return ((HttpContent) msg).content().readableBytes();
        }
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return (int) ((FileRegion) msg).count();
        }
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    private static void encodeHeaders(ByteBuf buf, HttpHeaders headers) {
        for (Map.Entry<String, String> h: headers) {
            encodeHeader(buf, h.getKey(), h.getValue());
        }
    }

    private static void encodeHeader(ByteBuf buf, String header, String value) {
        encodeAscii(header, buf);
        buf.writeBytes(HEADER_SEPARATOR);
        encodeAscii(value, buf);
        buf.writeBytes(CRLF);
    }

    protected static void encodeAscii(String s, ByteBuf buf) {
        for (int i = 0; i < s.length(); i++) {
            buf.writeByte(s.charAt(i));
        }
    }

    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;
}
