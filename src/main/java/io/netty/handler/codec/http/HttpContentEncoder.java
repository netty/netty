/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import java.util.Queue;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelHandler;
import io.netty.handler.codec.embedder.EncoderEmbedder;
import io.netty.util.internal.QueueFactory;

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
 * converts them into {@link ChannelBuffer}s.
 */
public abstract class HttpContentEncoder extends SimpleChannelHandler {

    private final Queue<String> acceptEncodingQueue = QueueFactory.createQueue(String.class);
    private volatile EncoderEmbedder<ChannelBuffer> encoder;

    /**
     * Creates a new instance.
     */
    protected HttpContentEncoder() {
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof HttpMessage)) {
            ctx.sendUpstream(e);
            return;
        }

        HttpMessage m = (HttpMessage) msg;
        String acceptedEncoding = m.getHeader(HttpHeaders.Names.ACCEPT_ENCODING);
        if (acceptedEncoding == null) {
            acceptedEncoding = HttpHeaders.Values.IDENTITY;
        }
        boolean offered = acceptEncodingQueue.offer(acceptedEncoding);
        assert offered;

        ctx.sendUpstream(e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Object msg = e.getMessage();
        if (msg instanceof HttpResponse && ((HttpResponse) msg).getStatus().getCode() == 100) {
            // 100-continue response must be passed through.
            ctx.sendDownstream(e);
        } else  if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            encoder = null;

            // Determine the content encoding.
            String acceptEncoding = acceptEncodingQueue.poll();
            if (acceptEncoding == null) {
                throw new IllegalStateException("cannot send more responses than requests");
            }

            boolean hasContent = m.isChunked() || m.getContent().readable();
            if (!hasContent) {
                ctx.sendDownstream(e);
                return;
            }

            Result result = beginEncode(m, acceptEncoding);
            if (result == null) {
                ctx.sendDownstream(e);
                return;
            }

            encoder = result.getContentEncoder();

            // Encode the content and remove or replace the existing headers
            // so that the message looks like a decoded message.
            m.setHeader(
                    HttpHeaders.Names.CONTENT_ENCODING,
                    result.getTargetContentEncoding());

            if (!m.isChunked()) {
                ChannelBuffer content = m.getContent();
                // Encode the content.
                content = ChannelBuffers.wrappedBuffer(
                        encode(content), finishEncode());

                // Replace the content.
                m.setContent(content);
                if (m.containsHeader(HttpHeaders.Names.CONTENT_LENGTH)) {
                    m.setHeader(
                            HttpHeaders.Names.CONTENT_LENGTH,
                            Integer.toString(content.readableBytes()));
                }
            }

            // Because HttpMessage is a mutable object, we can simply forward the write request.
            ctx.sendDownstream(e);
        } else if (msg instanceof HttpChunk) {
            HttpChunk c = (HttpChunk) msg;
            ChannelBuffer content = c.getContent();

            // Encode the chunk if necessary.
            if (encoder != null) {
                if (!c.isLast()) {
                    content = encode(content);
                    if (content.readable()) {
                        c.setContent(content);
                        ctx.sendDownstream(e);
                    }
                } else {
                    ChannelBuffer lastProduct = finishEncode();

                    // Generate an additional chunk if the decoder produced
                    // the last product on closure,
                    if (lastProduct.readable()) {
                        Channels.write(
                                ctx, Channels.succeededFuture(e.getChannel()), new DefaultHttpChunk(lastProduct), e.getRemoteAddress());
                    }

                    // Emit the last chunk.
                    ctx.sendDownstream(e);
                }
            } else {
                ctx.sendDownstream(e);
            }
        } else {
            ctx.sendDownstream(e);
        }
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

    private ChannelBuffer encode(ChannelBuffer buf) {
        encoder.offer(buf);
        return ChannelBuffers.wrappedBuffer(encoder.pollAll(new ChannelBuffer[encoder.size()]));
    }

    private ChannelBuffer finishEncode() {
        ChannelBuffer result;
        if (encoder.finish()) {
            result = ChannelBuffers.wrappedBuffer(encoder.pollAll(new ChannelBuffer[encoder.size()]));
        } else {
            result = ChannelBuffers.EMPTY_BUFFER;
        }
        encoder = null;
        return result;
    }

    public static final class Result {
        private final String targetContentEncoding;
        private final EncoderEmbedder<ChannelBuffer> contentEncoder;

        public Result(String targetContentEncoding, EncoderEmbedder<ChannelBuffer> contentEncoder) {
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

        public EncoderEmbedder<ChannelBuffer> getContentEncoder() {
            return contentEncoder;
        }
    }
}
