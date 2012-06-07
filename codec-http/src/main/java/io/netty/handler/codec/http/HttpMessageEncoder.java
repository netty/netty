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

import static io.netty.buffer.ChannelBuffers.*;
import static io.netty.handler.codec.http.HttpConstants.*;
import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToStreamEncoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.util.Map;

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
 * @apiviz.landmark
 */
public abstract class HttpMessageEncoder extends MessageToStreamEncoder<Object> {

    private static final ChannelBuffer LAST_CHUNK =
        copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII);

    private volatile boolean chunked;

    /**
     * Creates a new instance.
     */
    protected HttpMessageEncoder() {
    }

    @Override
    public boolean isEncodable(Object msg) throws Exception {
        return msg instanceof HttpMessage || msg instanceof HttpChunk;
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ChannelBuffer out) throws Exception {
        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;
            boolean chunked;
            if (m.isChunked()) {
                // check if the Transfer-Encoding is set to chunked already.
                // if not add the header to the message
                if (!HttpCodecUtil.isTransferEncodingChunked(m)) {
                    m.addHeader(Names.TRANSFER_ENCODING, Values.CHUNKED);
                }
                chunked = this.chunked = true;
            } else {
                chunked = this.chunked = HttpCodecUtil.isTransferEncodingChunked(m);
            }
            out.markWriterIndex();
            encodeInitialLine(out, m);
            encodeHeaders(out, m);
            out.writeByte(CR);
            out.writeByte(LF);

            ChannelBuffer content = m.getContent();
            if (content.readable()) {
                if (chunked) {
                    out.resetWriterIndex();
                    throw new IllegalArgumentException(
                            "HttpMessage.content must be empty " +
                            "if Transfer-Encoding is chunked.");
                } else {
                    out.writeBytes(content, content.readerIndex(), content.readableBytes());
                }
            }
        } else if (msg instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) msg;
            if (chunked) {
                if (chunk.isLast()) {
                    chunked = false;
                    if (chunk instanceof HttpChunkTrailer) {
                        out.writeByte((byte) '0');
                        out.writeByte(CR);
                        out.writeByte(LF);
                        encodeTrailingHeaders(out, (HttpChunkTrailer) chunk);
                        out.writeByte(CR);
                        out.writeByte(LF);
                    } else {
                        out.writeBytes(LAST_CHUNK, LAST_CHUNK.readerIndex(), LAST_CHUNK.readableBytes());
                    }
                } else {
                    ChannelBuffer content = chunk.getContent();
                    int contentLength = content.readableBytes();
                    out.writeBytes(copiedBuffer(Integer.toHexString(contentLength), CharsetUtil.US_ASCII));
                    out.writeByte(CR);
                    out.writeByte(LF);
                    out.writeBytes(content, content.readerIndex(), contentLength);
                    out.writeByte(CR);
                    out.writeByte(LF);
                }
            } else {
                if (!chunk.isLast()) {
                    ChannelBuffer chunkContent = chunk.getContent();
                    out.writeBytes(chunkContent, chunkContent.readerIndex(), chunkContent.readableBytes());
                }
            }
        } else {
            throw new UnsupportedMessageTypeException(msg, HttpMessage.class, HttpChunk.class);
        }
    }

    private static void encodeHeaders(ChannelBuffer buf, HttpMessage message) {
        try {
            for (Map.Entry<String, String> h: message.getHeaders()) {
                encodeHeader(buf, h.getKey(), h.getValue());
            }
        } catch (UnsupportedEncodingException e) {
            throw (Error) new Error().initCause(e);
        }
    }

    private static void encodeTrailingHeaders(ChannelBuffer buf, HttpChunkTrailer trailer) {
        try {
            for (Map.Entry<String, String> h: trailer.getHeaders()) {
                encodeHeader(buf, h.getKey(), h.getValue());
            }
        } catch (UnsupportedEncodingException e) {
            throw (Error) new Error().initCause(e);
        }
    }

    private static void encodeHeader(ChannelBuffer buf, String header, String value)
            throws UnsupportedEncodingException {
        buf.writeBytes(header.getBytes("ASCII"));
        buf.writeByte(COLON);
        buf.writeByte(SP);
        buf.writeBytes(value.getBytes("ASCII"));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }

    protected abstract void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception;
}
