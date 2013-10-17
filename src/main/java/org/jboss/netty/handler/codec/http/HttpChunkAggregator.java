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
package org.jboss.netty.handler.codec.http;

import static org.jboss.netty.channel.Channels.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.*;

import java.util.List;
import java.util.Map.Entry;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.util.CharsetUtil;

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
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpChunk oneway - - filters out
 */
public class HttpChunkAggregator extends SimpleChannelUpstreamHandler implements LifeCycleAwareChannelHandler {
    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private static final ChannelBuffer CONTINUE = ChannelBuffers.copiedBuffer(
            "HTTP/1.1 100 Continue\r\n\r\n", CharsetUtil.US_ASCII);

    private final int maxContentLength;
    private HttpMessage currentMessage;
    private boolean tooLongFrameFound;
    private ChannelHandlerContext ctx;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public HttpChunkAggregator(int maxContentLength) {
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
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {

        Object msg = e.getMessage();
        HttpMessage currentMessage = this.currentMessage;

        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;
            tooLongFrameFound = false;

            // Handle the 'Expect: 100-continue' header if necessary.
            // TODO: Respond with 413 Request Entity Too Large
            //   and discard the traffic or close the connection.
            //       No need to notify the upstream handlers - just log.
            //       If decoding a response, just throw an exception.
            if (is100ContinueExpected(m)) {
                write(ctx, succeededFuture(ctx.getChannel()), CONTINUE.duplicate());
            }

            if (m.isChunked()) {
                // A chunked message - remove 'Transfer-Encoding' header,
                // initialize the cumulative buffer, and wait for incoming chunks.
                HttpCodecUtil.removeTransferEncodingChunked(m);
                m.setChunked(false);
                this.currentMessage = m;
            } else {
                // Not a chunked message - pass through.
                this.currentMessage = null;
                ctx.sendUpstream(e);
            }
        } else if (msg instanceof HttpChunk) {
            // Sanity check
            if (currentMessage == null) {
                throw new IllegalStateException(
                        "received " + HttpChunk.class.getSimpleName() +
                        " without " + HttpMessage.class.getSimpleName());
            }
            HttpChunk chunk = (HttpChunk) msg;

            if (tooLongFrameFound) {
                if (chunk.isLast()) {
                    this.currentMessage = null;
                }
                return;
            }

            // Merge the received chunk into the content of the current message.
            ChannelBuffer content = currentMessage.getContent();

            if (content.readableBytes() > maxContentLength - chunk.getContent().readableBytes()) {
                tooLongFrameFound = true;

                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            // Append the content of the chunk
            appendToCumulation(chunk.getContent());

            if (chunk.isLast()) {
                this.currentMessage = null;

                // Merge trailing headers into the message.
                if (chunk instanceof HttpChunkTrailer) {
                    HttpChunkTrailer trailer = (HttpChunkTrailer) chunk;
                    for (Entry<String, String> header: trailer.trailingHeaders()) {
                        currentMessage.headers().set(header.getKey(), header.getValue());
                    }
                }

                // Set the 'Content-Length' header.
                currentMessage.headers().set(
                        HttpHeaders.Names.CONTENT_LENGTH,
                        String.valueOf(content.readableBytes()));

                // All done - generate the event.
                fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
            }
        } else {
            // Neither HttpMessage or HttpChunk
            ctx.sendUpstream(e);
        }
    }

    protected void appendToCumulation(ChannelBuffer input) {
        ChannelBuffer cumulation = currentMessage.getContent();
        if (cumulation instanceof CompositeChannelBuffer) {
            // Make sure the resulting cumulation buffer has no more than the configured components.
            CompositeChannelBuffer composite = (CompositeChannelBuffer) cumulation;
            if (composite.numComponents() >= maxCumulationBufferComponents) {
                currentMessage.setContent(ChannelBuffers.wrappedBuffer(composite.copy(), input));
            } else {
                List<ChannelBuffer> decomposed = composite.decompose(0, composite.readableBytes());
                ChannelBuffer[] buffers = decomposed.toArray(new ChannelBuffer[decomposed.size() + 1]);
                buffers[buffers.length - 1] = input;

                currentMessage.setContent(ChannelBuffers.wrappedBuffer(buffers));
            }
        } else {
            currentMessage.setContent(ChannelBuffers.wrappedBuffer(cumulation, input));
        }
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // noop
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        // noop
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // noop
    }
}
