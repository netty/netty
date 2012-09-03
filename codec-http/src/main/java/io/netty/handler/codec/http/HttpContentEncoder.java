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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Encodes the content of the outbound {@link HttpResponse} and {@link HttpChunk}.
 * The original content is replaced with the new content encoded by the
 * {@link EncoderEmbedder}, which is created by {@link #beginEncode(HttpMessage, String)}.
 * Once encoding is finished, the value of the <tt>'Content-Encoding'</tt> header
 * is set to the target content encoding, as returned by
 * {@link #beginEncode(HttpMessage, String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * encoded content.  If there is no supported or allowed encoding in the
 * corresponding {@link HttpRequest}'s {@code "Accept-Encoding"} header,
 * {@link #beginEncode(HttpMessage, String)} should return {@code null} so that
 * no encoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #beginEncode(HttpMessage, String)} properly to make
 * this class functional.  For example, refer to the source code of
 * {@link HttpContentCompressor}.
 * <p>
 * This handler must be placed after {@link HttpMessageEncoder} in the pipeline
 * so that this handler can intercept HTTP responses before {@link HttpMessageEncoder}
 * converts them into {@link ByteBuf}s.
 */
public abstract class HttpContentEncoder extends MessageToMessageCodec<HttpMessage, HttpMessage, Object, Object> {

    private final Queue<String> acceptEncodingQueue = new ArrayDeque<String>();
    private EmbeddedByteChannel encoder;

    /**
     * Creates a new instance.
     */
    protected HttpContentEncoder() {
    }

    @Override
    public boolean isDecodable(Object msg) throws Exception {
        return msg instanceof HttpMessage;
    }

    @Override
    public HttpMessage decode(ChannelHandlerContext ctx, HttpMessage msg)
            throws Exception {
        String acceptedEncoding = msg.getHeader(HttpHeaders.Names.ACCEPT_ENCODING);
        if (acceptedEncoding == null) {
            acceptedEncoding = HttpHeaders.Values.IDENTITY;
        }
        boolean offered = acceptEncodingQueue.offer(acceptedEncoding);
        assert offered;
        return msg;
    }

    @Override
    public boolean isEncodable(Object msg) throws Exception {
        return msg instanceof HttpMessage || msg instanceof HttpChunk;
    }

    @Override
    public Object encode(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (msg instanceof HttpResponse && ((HttpResponse) msg).getStatus().getCode() == 100) {
            // 100-continue response must be passed through.
            return msg;
        } else  if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            cleanup();

            // Determine the content encoding.
            String acceptEncoding = acceptEncodingQueue.poll();
            if (acceptEncoding == null) {
                throw new IllegalStateException("cannot send more responses than requests");
            }

            boolean hasContent = m.getTransferEncoding().isMultiple() || m.getContent().readable();
            if (!hasContent) {
                return m;
            }

            Result result = beginEncode(m, acceptEncoding);
            if (result == null) {
                return m;
            }

            encoder = result.getContentEncoder();

            // Encode the content and remove or replace the existing headers
            // so that the message looks like a decoded message.
            m.setHeader(
                    HttpHeaders.Names.CONTENT_ENCODING,
                    result.getTargetContentEncoding());

            if (m.getTransferEncoding().isSingle()) {
                ByteBuf content = m.getContent();
                // Encode the content.
                ByteBuf newContent = Unpooled.buffer();
                encode(content, newContent);
                finishEncode(newContent);

                // Replace the content.
                m.setContent(newContent);
                if (m.containsHeader(HttpHeaders.Names.CONTENT_LENGTH)) {
                    m.setHeader(
                            HttpHeaders.Names.CONTENT_LENGTH,
                            Integer.toString(newContent.readableBytes()));
                }
            }
        } else if (msg instanceof HttpChunk) {
            HttpChunk c = (HttpChunk) msg;
            ByteBuf content = c.getContent();

            // Encode the chunk if necessary.
            if (encoder != null) {
                if (!c.isLast()) {
                    ByteBuf newContent = Unpooled.buffer();
                    encode(content, newContent);
                    if (content.readable()) {
                        c.setContent(newContent);
                    } else {
                        return null;
                    }
                } else {
                    ByteBuf lastProduct = Unpooled.buffer();
                    finishEncode(lastProduct);

                    // Generate an additional chunk if the decoder produced
                    // the last product on closure,
                    if (lastProduct.readable()) {
                        return new Object[] { new DefaultHttpChunk(lastProduct), c };
                    }
                }
            }
        }

        // Because HttpMessage and HttpChunk is a mutable object, we can simply forward it.
        return msg;
    }

    /**
     * Prepare to encode the HTTP message content.
     *
     * @param msg
     *        the HTTP message whose content should be encoded
     * @param acceptEncoding
     *        the value of the {@code "Accept-Encoding"} header
     *
     * @return the result of preparation, which is composed of the determined
     *         target content encoding and a new {@link EncoderEmbedder} that
     *         encodes the content into the target content encoding.
     *         {@code null} if {@code acceptEncoding} is unsupported or rejected
     *         and thus the content should be handled as-is (i.e. no encoding).
     */
    protected abstract Result beginEncode(HttpMessage msg, String acceptEncoding) throws Exception;


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

        public String getTargetContentEncoding() {
            return targetContentEncoding;
        }

        public EmbeddedByteChannel getContentEncoder() {
            return contentEncoder;
        }
    }
}
