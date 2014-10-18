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
import io.netty.handler.codec.MessageToByteEncoder;
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
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToByteEncoder<Object> {
    private static final byte[] CRLF = { CR, LF };
    private static final byte[] ZERO_CRLF = { '0', CR, LF };
    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };
    private static final ByteBuf CRLF_BUF = unreleasableBuffer(directBuffer(CRLF.length).writeBytes(CRLF));

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;

    private final int initialBufferCapacity;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    protected HttpObjectEncoder() {
        this(0, 0);
    }

    protected HttpObjectEncoder(int initialBufferCapacity, int flushThreshold) {
        super(true, flushThreshold);
        this.initialBufferCapacity = initialBufferCapacity;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FileRegion) {
            writeBufferedBytes(ctx);
            switch (state) {
                case ST_INIT:
                    throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
                case ST_CONTENT_NON_CHUNK:
                    ctx.write(encodeAndRetain(msg), promise);
                    break;
                case ST_CONTENT_CHUNK:
                    long contentLength = contentLength(msg);
                    if (contentLength > 0) {
                        byte[] length = Long.toHexString(contentLength).getBytes(CharsetUtil.US_ASCII);
                        ByteBuf buf = ctx.alloc().buffer(length.length + 2);
                        buf.writeBytes(length);
                        buf.writeBytes(CRLF);
                        ctx.write(buf);
                        ctx.write(msg);
                        ctx.write(CRLF_BUF.duplicate(), promise);
                    }
                    break;
                default:
                    throw new Error();
            }
            return;
        }
        super.write(ctx, msg, promise);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf buf) throws Exception {
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;

            // Encode the message.
            encodeInitialLine(buf, m);
            HttpHeaders.encode(m.headers(), buf);
            buf.writeBytes(CRLF);
            state = HttpHeaders.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
        }
        if (msg instanceof HttpContent || msg instanceof ByteBuf) {
            if (state == ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            final long contentLength = contentLength(msg);
            if (state == ST_CONTENT_NON_CHUNK) {
                if (contentLength > 0) {
                    if (msg instanceof HttpContent) {
                        buf.writeBytes(((HttpContent) msg).content());
                    } else {
                        buf.writeBytes((ByteBuf) msg);
                    }
                }
                if (msg instanceof LastHttpContent) {
                    state = ST_INIT;
                }
            } else if (state == ST_CONTENT_CHUNK) {
                if (msg instanceof ByteBuf) {
                    if (contentLength > 0) {
                        encodeChunkedContent((ByteBuf) msg, contentLength, buf);
                    }
                } else {
                    HttpContent content = (HttpContent) msg;
                    if (contentLength > 0) {
                        encodeChunkedContent(content.content(), contentLength, buf);
                    }
                    if (content instanceof LastHttpContent) {
                        HttpHeaders headers = ((LastHttpContent) content).trailingHeaders();
                        if (headers.isEmpty()) {
                            buf.writeBytes(ZERO_CRLF_CRLF);
                        } else {
                            buf.writeBytes(ZERO_CRLF);
                            HttpHeaders.encode(headers, buf);
                            buf.writeBytes(CRLF);
                        }

                        state = ST_INIT;
                    }
                }
            } else {
                throw new Error();
            }
        }
    }

    private static void encodeChunkedContent(ByteBuf msg, long contentLength, ByteBuf buf) {
        byte[] length = Long.toHexString(contentLength).getBytes(CharsetUtil.US_ASCII);
        buf.writeBytes(length);
        buf.writeBytes(CRLF);
        buf.writeBytes(msg);
        buf.writeBytes(CRLF_BUF);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof HttpObject || msg instanceof ByteBuf;
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, @SuppressWarnings("unused") Object msg,
                                     boolean preferDirect) throws Exception {
        if (preferDirect) {
            if (initialBufferCapacity > 0) {
                return ctx.alloc().ioBuffer(initialBufferCapacity);
            }
            return ctx.alloc().ioBuffer();
        } else {
            if (initialBufferCapacity > 0) {
                return ctx.alloc().heapBuffer(initialBufferCapacity);
            }
            return ctx.alloc().heapBuffer();
        }
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

    private static long contentLength(Object msg) {
        if (msg instanceof HttpContent) {
            return ((HttpContent) msg).content().readableBytes();
        }
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof FileRegion) {
            return ((FileRegion) msg).count();
        }
        throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
    }

    @Deprecated
    protected static void encodeAscii(String s, ByteBuf buf) {
        HttpHeaders.encodeAscii0(s, buf);
    }

    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;
}
