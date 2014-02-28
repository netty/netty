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
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.MessageToBufferedByteEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;

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
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToBufferedByteEncoder<Object> {
    private static final byte[] CRLF = { CR, LF };
    private static final byte[] ZERO_CRLF = { '0', CR, LF };
    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(CRLF.length).writeBytes(CRLF));

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    protected HttpObjectEncoder() {
    }

    protected HttpObjectEncoder(int bufferSize) {
        super(bufferSize);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FileRegion) {
            // As we can't write a FileRegion into a ByteBuf in an efficient way we special handle it here
            // and write it directly. This will also first write all buffered data to make sure we keep the
            // correct order.
            writeFileRegion(ctx, (FileRegion) msg, promise);
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }
            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;
            // Encode the message.
            encodeInitialLine(out, m);
            HttpHeaders.encode(m.headers(), out);
            out.writeBytes(CRLF);
            state = HttpHeaders.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
        }
        if (msg instanceof HttpContent) {
            if (state == ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            HttpContent content = (HttpContent) msg;
            if (state == ST_CONTENT_NON_CHUNK) {
                out.writeBytes(content.content());

                if (msg instanceof LastHttpContent) {
                    state = ST_INIT;
                }
            } else if (state == ST_CONTENT_CHUNK) {
                encodeChunkedContent(content, out);
            } else {
                throw new Error();
            }
        } else if (msg instanceof ByteBuf) {
            ByteBuf content = (ByteBuf) msg;
            if (state == ST_CONTENT_NON_CHUNK) {
                out.writeBytes(content);
            } else if (state == ST_CONTENT_CHUNK) {
                encodeChunkedContent(content, out);
            } else {
                throw new Error();
            }
        }
    }


    private void encodeChunkedContent(HttpContent content, ByteBuf out) {
        encodeChunkedContent(content.content(), out);

        if (content instanceof LastHttpContent) {
            HttpHeaders headers = ((LastHttpContent) content).trailingHeaders();
            if (headers.isEmpty()) {
                out.writeBytes(ZERO_CRLF_CRLF);
            } else {
                out.writeBytes(ZERO_CRLF);
                HttpHeaders.encode(headers, out);
                out.writeBytes(CRLF);
            }
            state = ST_INIT;
        }
    }

    private static void encodeChunkedContent(ByteBuf content, ByteBuf out) {
        int contentLength = content.readableBytes();
        if (contentLength > 0) {
            byte[] length = Long.toHexString(contentLength).getBytes(CharsetUtil.US_ASCII);
            out.writeBytes(length);
            out.writeBytes(CRLF);
            out.writeBytes(content);
            out.writeBytes(CRLF);
        }
    }

    private void writeFileRegion(ChannelHandlerContext ctx, FileRegion region, ChannelPromise promise) {
        if (state == ST_INIT) {
            throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(region));
        } else if (state == ST_CONTENT_NON_CHUNK) {
            // write buffered data now to preserve correct order
            writeBufferedData(ctx);

            ctx.write(region, promise);
        } else if (state == ST_CONTENT_CHUNK) {
            long contentLength = region.count();
            if (contentLength > 0) {
                // write buffered data now to preserve correct order
                writeBufferedData(ctx);

                byte[] length = Long.toHexString(contentLength).getBytes(CharsetUtil.US_ASCII);
                ByteBuf out = ctx.alloc().buffer(length.length + 2);
                out.writeBytes(length);
                out.writeBytes(CRLF);
                ctx.write(out);
                ctx.write(region);
                ctx.write(CRLF_BUF.duplicate(), promise);
            }
        } else {
            throw new Error();
        }
    }


    @Deprecated
    protected static void encodeAscii(String s, ByteBuf buf) {
        HttpHeaders.encodeAscii0(s, buf);
    }

    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;
}
