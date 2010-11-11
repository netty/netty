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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;

/**
 * Decodes the content of the received {@link HttpRequest} and {@link HttpChunk}.
 * The original content is replaced with the new content decoded by the
 * {@link DecoderEmbedder}, which is created by {@link #newContentDecoder(String)}.
 * Once decoding is finished, the value of the <tt>'Content-Encoding'</tt>
 * header is set to the target content encoding, as returned by {@link #getTargetContentEncoding(String)}.
 * Also, the <tt>'Content-Length'</tt> header is updated to the length of the
 * decoded content.  If the content encoding of the original is not supported
 * by the decoder, {@link #newContentDecoder(String)} should return {@code null}
 * so that no decoding occurs (i.e. pass-through).
 * <p>
 * Please note that this is an abstract class.  You have to extend this class
 * and implement {@link #newContentDecoder(String)} properly to make this class
 * functional.  For example, refer to the source code of {@link HttpContentDecompressor}.
 * <p>
 * This handler must be placed after {@link HttpMessageDecoder} in the pipeline
 * so that this handler can intercept HTTP requests after {@link HttpMessageDecoder}
 * converts {@link ChannelBuffer}s into HTTP requests.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2368 $, $Date: 2010-10-18 17:19:03 +0900 (Mon, 18 Oct 2010) $
 */
public abstract class HttpContentDecoder extends SimpleChannelUpstreamHandler {

    private DecoderEmbedder<ChannelBuffer> decoder;

    /**
     * Creates a new instance.
     */
    protected HttpContentDecoder() {
        super();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpResponse && ((HttpResponse) msg).getStatus().getCode() == 100) {
            // 100-continue response must be passed through.
            ctx.sendDownstream(e);
        } else if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            decoder = null;

            // Determine the content encoding.
            String contentEncoding = m.getHeader(HttpHeaders.Names.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            } else {
                contentEncoding = HttpHeaders.Values.IDENTITY;
            }

            boolean hasContent = m.isChunked() || m.getContent().readable();
            if (hasContent && (decoder = newContentDecoder(contentEncoding)) != null) {
                // Decode the content and remove or replace the existing headers
                // so that the message looks like a decoded message.
                m.setHeader(
                        HttpHeaders.Names.CONTENT_ENCODING,
                        getTargetContentEncoding(contentEncoding));

                if (!m.isChunked()) {
                    ChannelBuffer content = m.getContent();
                    // Decode the content
                    content = ChannelBuffers.wrappedBuffer(
                            decode(content), finishDecode());

                    // Replace the content.
                    m.setContent(content);
                    if (m.containsHeader(HttpHeaders.Names.CONTENT_LENGTH)) {
                        m.setHeader(
                                HttpHeaders.Names.CONTENT_LENGTH,
                                Integer.toString(content.readableBytes()));
                    }
                }
            }

            // Because HttpMessage is a mutable object, we can simply forward the received event.
            ctx.sendUpstream(e);
        } else if (msg instanceof HttpChunk) {
            HttpChunk c = (HttpChunk) msg;
            ChannelBuffer content = c.getContent();

            // Decode the chunk if necessary.
            if (decoder != null) {
                if (!c.isLast()) {
                    content = decode(content);
                    if (content.readable()) {
                        c.setContent(content);
                        ctx.sendUpstream(e);
                    }
                } else {
                    ChannelBuffer lastProduct = finishDecode();

                    // Generate an additional chunk if the decoder produced
                    // the last product on closure,
                    if (lastProduct.readable()) {
                        Channels.fireMessageReceived(
                                ctx, new DefaultHttpChunk(lastProduct), e.getRemoteAddress());
                    }

                    // Emit the last chunk.
                    ctx.sendUpstream(e);
                }
            } else {
                ctx.sendUpstream(e);
            }
        } else {
            ctx.sendUpstream(e);
        }
    }

    /**
     * Returns a new {@link DecoderEmbedder} that decodes the HTTP message
     * content encoded in the specified <tt>contentEncoding</tt>.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return a new {@link DecoderEmbedder} if the specified encoding is supported.
     *         {@code null} otherwise (alternatively, you can throw an exception
     *         to block unknown encoding).
     */
    protected abstract DecoderEmbedder<ChannelBuffer> newContentDecoder(String contentEncoding) throws Exception;

    /**
     * Returns the expected content encoding of the decoded content.
     * This method returns {@code "identity"} by default, which is the case for
     * most decoders.
     *
     * @param contentEncoding the value of the {@code "Content-Encoding"} header
     * @return the expected content encoding of the new content
     */
    protected String getTargetContentEncoding(String contentEncoding) throws Exception {
        return HttpHeaders.Values.IDENTITY;
    }

    private ChannelBuffer decode(ChannelBuffer buf) {
        decoder.offer(buf);
        return ChannelBuffers.wrappedBuffer(decoder.pollAll(new ChannelBuffer[decoder.size()]));
    }

    private ChannelBuffer finishDecode() {
        ChannelBuffer result;
        if (decoder.finish()) {
            result = ChannelBuffers.wrappedBuffer(decoder.pollAll(new ChannelBuffer[decoder.size()]));
        } else {
            result = ChannelBuffers.EMPTY_BUFFER;
        }
        decoder = null;
        return result;
    }
}
