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

import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.jboss.netty.handler.codec.http.HttpCodecUtil.*;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * Encodes an {@link HttpMessage} or an {@link HttpChunk} into
 * a {@link ChannelBuffer}.
 *
 * <h3>Extensibility</h3>
 *
 * Please note that this encoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the encoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
@ChannelPipelineCoverage("all")
public abstract class HttpMessageEncoder extends OneToOneEncoder {

    private static final ChannelBuffer LAST_CHUNK = copiedBuffer("0\r\n\r\n", "ASCII");

    /**
     * Creates a new instance.
     */
    protected HttpMessageEncoder() {
        super();
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof HttpMessage) {
            HttpMessage request = (HttpMessage) msg;
            ChannelBuffer header = ChannelBuffers.dynamicBuffer(
                    channel.getConfig().getBufferFactory());
            encodeInitialLine(header, request);
            encodeHeaders(header, request);
            header.writeBytes(CRLF);

            ChannelBuffer content = request.getContent();
            if (!content.readable()) {
                return header; // no content
            } else {
                return wrappedBuffer(header, content);
            }
        }

        if (msg instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) msg;
            if (chunk == HttpChunk.LAST_CHUNK) {
                return LAST_CHUNK.duplicate();
            } else if (chunk instanceof HttpChunkTrailer) {
                ChannelBuffer trailer = ChannelBuffers.dynamicBuffer(
                        channel.getConfig().getBufferFactory());
                trailer.writeByte((byte) '0');
                trailer.writeBytes(CRLF);
                encodeTrailingHeaders(trailer, (HttpChunkTrailer) chunk);
                trailer.writeBytes(CRLF);
                return trailer;
            } else {
                ChannelBuffer content = chunk.getContent();
                int contentLength = content.readableBytes();

                return wrappedBuffer(
                        copiedBuffer(Integer.toHexString(contentLength), "ASCII"),
                        wrappedBuffer(CRLF),
                        content.slice(content.readerIndex(), contentLength),
                        wrappedBuffer(CRLF));
            }
        }

        // Unknown message type.
        return msg;
    }

    private void encodeHeaders(ChannelBuffer buf, HttpMessage message) {
        Set<String> headers = message.getHeaderNames();
        try {
            for (String header : headers) {
                List<String> values = message.getHeaders(header);
                for (String value : values) {
                    encodeHeader(buf, header, value);
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw (Error) new Error().initCause(e);
        }
    }

    private void encodeTrailingHeaders(ChannelBuffer buf, HttpChunkTrailer trailer) {
        Set<String> headers = trailer.getHeaderNames();
        try {
            for (String header : headers) {
                List<String> values = trailer.getHeaders(header);
                for (String value : values) {
                    encodeHeader(buf, header, value);
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw (Error) new Error().initCause(e);
        }
    }

    private void encodeHeader(ChannelBuffer buf, String header, String value)
            throws UnsupportedEncodingException {
        buf.writeBytes(header.getBytes("ASCII"));
        buf.writeByte(COLON);
        buf.writeByte(SP);
        buf.writeBytes(value.getBytes("ASCII"));
        buf.writeBytes(CRLF);
    }

    protected abstract void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception;
}
