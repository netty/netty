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
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Encodes the content of the outbound {@link HttpResponse} and {@link HttpContent}.
 * The original content is replaced with the new content encoded by the
 * {@link EmbeddedByteChannel}, which is created by {@link #beginEncode(HttpMessage, HttpContent, String)}.
 * Once encoding is finished, the value of the <tt>'Content-Encoding'</tt> header
 * is set to the target content encoding, as returned by
 * {@link #beginEncode(HttpMessage, HttpContent, String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * encoded content.  If there is no supported or allowed encoding in the
 * corresponding {@link HttpRequest}'s {@code "Accept-Encoding"} header,
 * {@link #beginEncode(HttpMessage, HttpContent, String)} should return {@code null} so that
 * no encoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #beginEncode(HttpMessage, HttpContent, String)} properly to make
 * this class functional.  For example, refer to the source code of
 * {@link HttpContentCompressor}.
 * <p>
 * This handler must be placed after {@link HttpObjectEncoder} in the pipeline
 * so that this handler can intercept HTTP responses before {@link HttpObjectEncoder}
 * converts them into {@link ByteBuf}s.
 */
public abstract class HttpContentEncoder extends MessageToMessageCodec<HttpMessage, Object> {

    private final Queue<String> acceptEncodingQueue = new ArrayDeque<String>();
    private EmbeddedByteChannel encoder;
    private HttpMessage message;
    private boolean encodeStarted;

    /**
     * Creates a new instance.
     */
    protected HttpContentEncoder() {
        super(
                new Class<?>[] { HttpMessage.class },
                new Class<?>[] { HttpObject.class });
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, HttpMessage msg)
            throws Exception {
        String acceptedEncoding = msg.headers().get(HttpHeaders.Names.ACCEPT_ENCODING);
        if (acceptedEncoding == null) {
            acceptedEncoding = HttpHeaders.Values.IDENTITY;
        }
        boolean offered = acceptEncodingQueue.offer(acceptedEncoding);
        assert offered;
        return msg;
    }

    @Override
    public Object encode(ChannelHandlerContext ctx, Object msg)
            throws Exception {

        if (msg instanceof HttpResponse && ((HttpResponse) msg).getStatus().code() == 100) {
            // 100-continue response must be passed through.
            return msg;
        }
        if (msg instanceof HttpMessage) {
            assert message == null;

            // check if this message is also of type HttpContent is such case just make a safe copy of the headers
            // as the content will get handled later and this simplify the handling
            if (msg instanceof HttpContent) {
                if (msg instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) msg;
                    message = new DefaultHttpRequest(req.getProtocolVersion(), req.getMethod(), req.getUri());
                    message.headers().set(req.headers());
                } else  if (msg instanceof HttpResponse) {
                    HttpResponse res = (HttpResponse) msg;
                    message = new DefaultHttpResponse(res.getProtocolVersion(), res.getStatus());
                    message.headers().set(res.headers());
                } else {
                    return msg;
                }
            } else {
                message = (HttpMessage) msg;
            }

            cleanup();
        }

        if (msg instanceof HttpContent) {
            HttpContent c = (HttpContent) msg;

            if (!encodeStarted) {
                encodeStarted = true;
                HttpMessage message = this.message;
                HttpHeaders headers = message.headers();
                this.message = null;

                // Determine the content encoding.
                String acceptEncoding = acceptEncodingQueue.poll();
                if (acceptEncoding == null) {
                    throw new IllegalStateException("cannot send more responses than requests");
                }
                Result result = beginEncode(message, c, acceptEncoding);

                if (result == null) {
                    if (c instanceof LastHttpContent) {
                        return new Object[] { message, new DefaultLastHttpContent(c.data()) };
                    } else {
                        return new Object[] { message, new DefaultHttpContent(c.data()) };
                    }
                }

                encoder = result.contentEncoder();

                // Encode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                headers.set(
                        HttpHeaders.Names.CONTENT_ENCODING,
                        result.targetContentEncoding());

                Object[] encoded = encodeContent(message, c);

                if (!HttpHeaders.isTransferEncodingChunked(message) && encoded.length == 3) {
                    if (headers.contains(HttpHeaders.Names.CONTENT_LENGTH)) {
                        long length = ((ByteBufHolder) encoded[1]).data().readableBytes() +
                                ((ByteBufHolder) encoded[2]).data().readableBytes();

                        headers.set(
                                HttpHeaders.Names.CONTENT_LENGTH,
                                Long.toString(length));
                    }
                }
                return encoded;
            }
            if (encoder != null) {
                return encodeContent(null, c);
            }
            return msg;
        }
        return null;
    }

    @Override
    protected void freeOutboundMessage(Object msg) throws Exception {
        if (encoder == null) {
            // if the decoder was null we returned the original message so we are not allowed to free it
            return;
        }
        super.freeOutboundMessage(msg);
    }

    @Override
    protected void freeInboundMessage(HttpMessage msg) throws Exception {
        // not free it as it is only passed through
    }

    private Object[] encodeContent(HttpMessage header, HttpContent c) {
        ByteBuf newContent = Unpooled.buffer();
        ByteBuf content = c.data();
        encode(content, newContent);

        if (c instanceof LastHttpContent) {
            ByteBuf lastProduct = Unpooled.buffer();
            finishEncode(lastProduct);

            // Generate an additional chunk if the decoder produced
            // the last product on closure,
            if (lastProduct.readable()) {
                if (header == null) {
                    return new Object[] { new DefaultHttpContent(newContent), new DefaultLastHttpContent(lastProduct)};
                } else {
                    return new Object[] { header,  new DefaultHttpContent(newContent),
                            new DefaultLastHttpContent(lastProduct)};
                }
            }
        }
        if (header == null) {
            return new Object[] { new DefaultHttpContent(newContent) };
        } else {
            return new Object[] { header, new DefaultHttpContent(newContent) };
        }
    }

    /**
     * Prepare to encode the HTTP message content.
     *
     * @param header
     *        the header
     * @param msg
     *        the HTTP message whose content should be encoded
     * @param acceptEncoding
     *        the value of the {@code "Accept-Encoding"} header
     *
     * @return the result of preparation, which is composed of the determined
     *         target content encoding and a new {@link EmbeddedByteChannel} that
     *         encodes the content into the target content encoding.
     *         {@code null} if {@code acceptEncoding} is unsupported or rejected
     *         and thus the content should be handled as-is (i.e. no encoding).
     */
    protected abstract Result beginEncode(HttpMessage header, HttpContent msg, String acceptEncoding) throws Exception;

    @Override
    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.afterRemove(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cleanup();
        super.channelInactive(ctx);
    }

    private void cleanup() {
        if (encoder != null) {
            // Clean-up the previous encoder if not cleaned up correctly.
            finishEncode(Unpooled.buffer());
        }
    }

    private void encode(ByteBuf in, ByteBuf out) {
        encoder.writeOutbound(in);
        fetchEncoderOutput(out);
    }

    private void finishEncode(ByteBuf out) {
        if (encoder.finish()) {
            fetchEncoderOutput(out);
        }
        encodeStarted = false;
        encoder = null;
    }

    private void fetchEncoderOutput(ByteBuf out) {
        for (;;) {
            ByteBuf buf = encoder.readOutbound();
            if (buf == null) {
                break;
            }
            out.writeBytes(buf);
        }
    }

    public static final class Result {
        private final String targetContentEncoding;
        private final EmbeddedByteChannel contentEncoder;

        public Result(String targetContentEncoding, EmbeddedByteChannel contentEncoder) {
            if (targetContentEncoding == null) {
                throw new NullPointerException("targetContentEncoding");
            }
            if (contentEncoder == null) {
                throw new NullPointerException("contentEncoder");
            }

            this.targetContentEncoding = targetContentEncoding;
            this.contentEncoder = contentEncoder;
        }

        public String targetContentEncoding() {
            return targetContentEncoding;
        }

        public EmbeddedByteChannel contentEncoder() {
            return contentEncoder;
        }
    }
}
