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

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;

/**
 * A {@link ChannelHandler} that aggregates an {@link HttpMessage}
 * and its following {@link HttpChunk}s into a single {@link HttpMessage} with
 * no following {@link HttpChunk}s.  It is useful when you don't want to take
 * care of HTTP messages whose transfer encoding is 'chunked'.  Insert this
 * handler after {@link HttpMessageDecoder} in the {@link ChannelPipeline}:
 * <pre>
 * ChannelPipeline p = ...;
 * ...
 * p.addLast("decoder", new HttpRequestDecoder());
 * p.addLast("aggregator", <b>new HttpChunkAggregator(1048576)</b>);
 * ...
 * p.addLast("encoder", new HttpResponseEncoder());
 * p.addLast("handler", new HttpRequestHandler());
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpChunk oneway - - filters out
 */
@ChannelPipelineCoverage("one")
public class HttpChunkAggregator extends SimpleChannelUpstreamHandler {

    private final int maxContentLength;
    private volatile HttpMessage currentMessage;

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

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof HttpMessage) && !(msg instanceof HttpChunk)) {
            ctx.sendUpstream(e);
            return;
        }

        HttpMessage currentMessage = this.currentMessage;
        if (currentMessage == null) {
            HttpMessage m = (HttpMessage) msg;
            if (m.isChunked()) {
                // A chunked message - remove 'Transfer-Encoding' header,
                // initialize the cumulative buffer, and wait for incoming chunks.
                List<String> encodings = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
                encodings.remove(HttpHeaders.Values.CHUNKED);
                if (encodings.isEmpty()) {
                    m.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
                }
                m.setContent(ChannelBuffers.EMPTY_BUFFER);
                this.currentMessage = m;
            } else {
                // Not a chunked message - pass through.
                ctx.sendUpstream(e);
            }
        } else {
            // Merge the received chunk into the content of the current message.
            HttpChunk chunk = (HttpChunk) msg;
            ChannelBuffer content = currentMessage.getContent();

            if (content.readableBytes() > maxContentLength - chunk.getContent().readableBytes()) {
                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            currentMessage.setContent(ChannelBuffers.wrappedBuffer(content, chunk.getContent()));
            if (chunk.isLast()) {
                this.currentMessage = null;
                currentMessage.setHeader(
                        HttpHeaders.Names.CONTENT_LENGTH,
                        String.valueOf(content.readableBytes()));
                Channels.fireMessageReceived(ctx, currentMessage, e.getRemoteAddress());
            }
        }
    }
}
