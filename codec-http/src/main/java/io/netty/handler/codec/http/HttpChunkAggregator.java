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

import static io.netty.handler.codec.http.HttpHeaders.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;

import java.util.Map.Entry;

/**
 * A {@link ChannelHandler} that aggregates an {@link HttpMessage}
 * and its following {@link HttpChunk}s into a single {@link HttpMessage} with
 * no following {@link HttpChunk}s.  It is useful when you don't want to take
 * care of HTTP messages whose transfer encoding is 'chunked'.  Insert this
 * handler after {@link HttpMessageDecoder} in the {@link ChannelPipeline}:
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * ...
 * p.addLast("decoder", new {@link HttpRequestDecoder}());
 * p.addLast("aggregator", <b>new {@link HttpChunkAggregator}(1048576)</b>);
 * ...
 * p.addLast("encoder", new {@link HttpResponseEncoder}());
 * p.addLast("handler", new HttpRequestHandler());
 * </pre>
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.codec.http.HttpChunk oneway - - filters out
 */
public class HttpChunkAggregator extends MessageToMessageDecoder<HttpObject, HttpMessage> {
    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;
    private static final ByteBuf CONTINUE = Unpooled.copiedBuffer(
            "HTTP/1.1 100 Continue\r\n\r\n", CharsetUtil.US_ASCII);

    private final int maxContentLength;
    private HttpMessage currentMessage;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;
    private ChannelHandlerContext ctx;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public HttpChunkAggregator(int maxContentLength) {
        super(HttpObject.class);

        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " +
                    maxContentLength);
        }
        this.maxContentLength = maxContentLength;
    }

    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int getMaxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    @Override
    public HttpMessage decode(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        HttpMessage currentMessage = this.currentMessage;

        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            // Handle the 'Expect: 100-continue' header if necessary.
            // TODO: Respond with 413 Request Entity Too Large
            //   and discard the traffic or close the connection.
            //       No need to notify the upstream handlers - just log.
            //       If decoding a response, just throw an exception.
            if (is100ContinueExpected(m)) {
                ctx.write(CONTINUE.duplicate());
            }

            if (!m.getDecoderResult().isSuccess()) {
                m.setTransferEncoding(HttpTransferEncoding.SINGLE);
                this.currentMessage = null;
                return m;
            }

            switch (m.getTransferEncoding()) {
            case SINGLE:
                this.currentMessage = null;
                return m;
            case STREAMED:
            case CHUNKED:
                // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
                m.setTransferEncoding(HttpTransferEncoding.SINGLE);
                m.setContent(Unpooled.compositeBuffer(maxCumulationBufferComponents));
                this.currentMessage = m;
                return null;
            default:
                throw new Error();
            }
        } else if (msg instanceof HttpChunk) {
            // Sanity check
            if (currentMessage == null) {
                throw new IllegalStateException(
                        "received " + HttpChunk.class.getSimpleName() +
                        " without " + HttpMessage.class.getSimpleName() +
                        " or last message's transfer encoding was 'SINGLE'");
            }

            // Merge the received chunk into the content of the current message.
            HttpChunk chunk = (HttpChunk) msg;
            ByteBuf content = currentMessage.getContent();

            if (content.readableBytes() > maxContentLength - chunk.getContent().readableBytes()) {
                // TODO: Respond with 413 Request Entity Too Large
                //   and discard the traffic or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            // Append the content of the chunk
            appendToCumulation(chunk.getContent());

            final boolean last;
            if (!chunk.getDecoderResult().isSuccess()) {
                currentMessage.setDecoderResult(
                        DecoderResult.partialFailure(chunk.getDecoderResult().cause()));
                last = true;
            } else {
                last = chunk.isLast();
            }

            if (last) {
                this.currentMessage = null;

                // Merge trailing headers into the message.
                if (chunk instanceof HttpChunkTrailer) {
                    HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
                    for (Entry<String, String> header: trailer.getHeaders()) {
                        currentMessage.setHeader(header.getKey(), header.getValue());
                    }
                }

                // Set the 'Content-Length' header.
                currentMessage.setHeader(
                        HttpHeaders.Names.CONTENT_LENGTH,
                        String.valueOf(content.readableBytes()));

                // All done
                return currentMessage;
            } else {
                return null;
            }
        } else {
            throw new IllegalStateException(
                    "Only " + HttpMessage.class.getSimpleName() + " and " +
                    HttpChunk.class.getSimpleName() + " are accepted: " + msg.getClass().getName());
        }
    }

    private void appendToCumulation(ByteBuf input) {
        CompositeByteBuf cumulation = (CompositeByteBuf) currentMessage.getContent();
        cumulation.addComponent(input);
        cumulation.writerIndex(cumulation.capacity());
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

}
