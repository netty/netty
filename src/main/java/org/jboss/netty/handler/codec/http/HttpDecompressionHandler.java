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
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;

/**
 * Decompresses an {@link HttpMessage} and an {@link HttpChunk} compressed in
 * {@code gzip}, {@code deflate}, and {@code compress} encoding.  During the
 * decompression, the {@code "Content-Encoding"} header is removed and the
 * value of the {@code "Content-Length"} header is updated with the length
 * of the decompressed content.  If the received message is not compressed,
 * no modification is made.  To use this handler, place it after
 * {@link HttpMessageDecoder} in the pipeline.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class HttpDecompressionHandler extends SimpleChannelUpstreamHandler {

    private volatile HttpMessage previous;
    private volatile int previousEncoding; // 0 - no compression, 1 - gzip, 2 - deflate, 3 - lzw
    private volatile DecoderEmbedder<ChannelBuffer> inflater;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;

            // Determine the content encoding.
            String contentEncoding = m.getHeader(HttpHeaders.Names.CONTENT_ENCODING);
            if (contentEncoding != null) {
                contentEncoding = contentEncoding.trim();
            }

            int encoding;
            if ("gzip".equalsIgnoreCase(contentEncoding) || "x-gzip".equalsIgnoreCase(contentEncoding)) {
                encoding = 1;
            } else if ("deflate".equalsIgnoreCase(contentEncoding) || "x-deflate".equalsIgnoreCase(contentEncoding)) {
                encoding = 2;
            } else {
                // FIXME Implement 'compress' encoding (lzw)
                encoding = 0;
            }

            if (m.isChunked()) {
                previous = m;
                previousEncoding = encoding;
            } else {
                previous = null;
                previousEncoding = 0;
            }

            // Decompress the content and remove or replace the existing headers
            // so that the message looks like an uncompressed message.
            if (encoding != 0) {
                m.removeHeader(HttpHeaders.Names.CONTENT_ENCODING);
                ChannelBuffer content = m.getContent();
                if (content.readable()) {
                    beginDecompression(encoding);
                    content = decompress(content);
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
            assert previous != null;

            HttpChunk c = (HttpChunk) msg;
            ChannelBuffer content = c.getContent();

            // Decompress the chunk if necessary.
            if (previousEncoding != 0) {
                if (!c.isLast()) {
                    content = decompress(content);
                    // HttpChunk is immutable unlike HttpMessage.
                    // XXX API inconsistency? I can live with it though.
                    Channels.fireMessageReceived(ctx, new DefaultHttpChunk(content), e.getRemoteAddress());
                } else {
                    finishDecompression();
                    previous = null;
                    previousEncoding = 0;
                    ctx.sendUpstream(e);
                }
            } else {
                // No need to
                ctx.sendUpstream(e);
            }
        } else {
            ctx.sendUpstream(e);
        }
    }

    private void beginDecompression(int encoding) {
        switch (encoding) {
        case 1:
            inflater = new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(ZlibWrapper.GZIP));
            break;
        case 2:
            inflater = new DecoderEmbedder<ChannelBuffer>(new ZlibDecoder(ZlibWrapper.ZLIB));
            break;
        default:
            throw new Error();
        }
    }

    private ChannelBuffer decompress(ChannelBuffer buf) {
        inflater.offer(buf);
        // FIXME Some inflater might produce either an empty buffer or many buffers.
        //       Empty buffer should not generate an event and many buffers should generate many events.
        return inflater.poll();
    }

    private void finishDecompression() {
        if (inflater.finish()) {
            throw new IllegalStateException("trailing data produced by inflater");
        }
        // TODO Make sure ZlibDecoder.isClosed() is true.
        //      The compressed stream ended prematurely if false.
    }
}
