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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromiseAggregatorFactory;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.TypeSensitiveMessageEncoder;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.util.Iterator;
import java.util.Map.Entry;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;

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
public abstract class HttpObjectEncoder<H extends HttpMessage> extends TypeSensitiveMessageEncoder<Object> {
    private static final byte[] CRLF = { CR, LF };
    private static final byte[] ZERO_CRLF = { '0', CR, LF };
    private static final byte[] ZERO_CRLF_CRLF = { '0', CR, LF, CR, LF };

    private static final int ST_INIT = 0;
    private static final int ST_CONTENT_NON_CHUNK = 1;
    private static final int ST_CONTENT_CHUNK = 2;

    @SuppressWarnings("RedundantFieldInitialization")
    private int state = ST_INIT;

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg,
                                ChannelPromiseAggregatorFactory promiseFactory) throws Exception {
        ByteBuf buf = null;
        if (msg instanceof HttpMessage) {
            if (state != ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            @SuppressWarnings({ "unchecked", "CastConflictsWithInstanceof" })
            H m = (H) msg;

            buf = ctx.alloc().buffer();
            // Encode the message.
            encodeInitialLine(buf, m);
            encodeHeaders(m.headers(), buf);
            buf.writeBytes(CRLF);
            state = HttpUtil.isTransferEncodingChunked(m) ? ST_CONTENT_CHUNK : ST_CONTENT_NON_CHUNK;
        }

        // Bypass the encoder in case of an empty buffer, so that the following idiom works:
        //
        //     ch.write(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        //
        // See https://github.com/netty/netty/issues/2983 for more information.

        if (msg instanceof ByteBuf && !((ByteBuf) msg).isReadable()) {
            ctx.write(EMPTY_BUFFER, promiseFactory.newPromise());
            return;
        }

        if (msg instanceof HttpContent || msg instanceof ByteBuf || msg instanceof FileRegion) {

            if (state == ST_INIT) {
                throw new IllegalStateException("unexpected message type: " + StringUtil.simpleClassName(msg));
            }

            final long contentLength = contentLength(msg);
            if (state == ST_CONTENT_NON_CHUNK) {
                if (contentLength > 0) {
                    if (buf != null && buf.writableBytes() >= contentLength && msg instanceof HttpContent) {
                        // merge into other buffer for performance reasons
                        buf.writeBytes(((HttpContent) msg).content());
                        ctx.write(buf, promiseFactory.newPromise());
                    } else {
                        if (buf != null) {
                            ctx.write(buf, promiseFactory.newPromise());
                        }
                        ctx.write(encodeAndRetain(msg), promiseFactory.newPromise());
                    }
                } else {
                    if (buf != null) {
                        ctx.write(buf, promiseFactory.newPromise());
                    } else {
                        // Need to produce some output otherwise an
                        // IllegalStateException will be thrown
                        ctx.write(EMPTY_BUFFER, promiseFactory.newPromise());
                    }
                }

                if (msg instanceof LastHttpContent) {
                    state = ST_INIT;
                }
            } else if (state == ST_CONTENT_CHUNK) {
                if (buf != null) {
                    ctx.write(buf, promiseFactory.newPromise());
                }
                encodeChunkedContent(ctx, msg, contentLength, promiseFactory);
            } else {
                throw new Error();
            }
        } else if (buf != null) {
            ctx.write(buf, promiseFactory.newPromise());
        }
    }

    /**
     * Encode the {@link HttpHeaders} into a {@link ByteBuf}.
     */
    protected void encodeHeaders(HttpHeaders headers, ByteBuf buf) throws Exception {
        Iterator<Entry<CharSequence, CharSequence>> iter = headers.iteratorCharSequence();
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> header = iter.next();
            HttpHeadersEncoder.encoderHeader(header.getKey(), header.getValue(), buf);
        }
    }

    private void encodeChunkedContent(ChannelHandlerContext ctx, Object msg, long contentLength,
                                      ChannelPromiseAggregatorFactory promiseFactory) throws Exception {
        if (contentLength > 0) {
            String length = Long.toHexString(contentLength);
            ByteBuf buf = ctx.alloc().buffer(length.length() + CRLF.length);
            ByteBufUtil.writeAscii(buf, length);
            ctx.write(buf, promiseFactory.newPromise());
            ctx.write(encodeAndRetain(msg), promiseFactory.newPromise());
            ctx.write(ctx.alloc().buffer(CRLF.length).writeBytes(CRLF));
        }

        if (msg instanceof LastHttpContent) {
            HttpHeaders headers = ((LastHttpContent) msg).trailingHeaders();
            if (headers.isEmpty()) {
                ctx.write(ctx.alloc().buffer(ZERO_CRLF_CRLF.length).writeBytes(ZERO_CRLF_CRLF));
            } else {
                // rough estimate that each header entry will require 15 characters to encode.
                ByteBuf buf = ctx.alloc().buffer(ZERO_CRLF.length + headers.size() * 15 + CRLF.length);
                buf.writeBytes(ZERO_CRLF);
                try {
                    encodeHeaders(headers, buf);
                } catch (Exception e) {
                    buf.release();
                    throw e;
                } catch (Throwable cause) {
                    buf.release();
                    PlatformDependent.throwException(cause);
                }
                buf.writeBytes(CRLF);
                ctx.write(buf, promiseFactory.newPromise());
            }

            state = ST_INIT;
        } else if (contentLength == 0) {
            // Need to produce some output otherwise an
            // IllegalstateException will be thrown
            ctx.write(EMPTY_BUFFER, promiseFactory.newPromise());
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
        HttpUtil.encodeAscii0(s, buf);
    }

    protected abstract void encodeInitialLine(ByteBuf buf, H message) throws Exception;
}
