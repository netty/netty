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

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpConstants.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.CharsetUtil;

import java.util.Map;

/**
 * Encodes an {@link HttpMessage} or an {@link HttpChunk} into
 * a {@link ByteBuf}.
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
public abstract class HttpMessageEncoder extends MessageToByteEncoder<Object> {

    private static final ByteBuf LAST_CHUNK =
        copiedBuffer("0\r\n\r\n", CharsetUtil.US_ASCII);

    private HttpTransferEncoding lastTE;

    /**
     * Creates a new instance.
     */
    protected HttpMessageEncoder() {
        super(HttpMessage.class, HttpChunk.class);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof HttpMessage) {
            HttpMessage m = (HttpMessage) msg;
            HttpTransferEncoding te = m.getTransferEncoding();
            lastTE = te;
            // Calling setTransferEncoding() will sanitize the headers and the content.
            // For example, it will remove the cases such as 'Transfer-Encoding' and 'Content-Length'
            // coexist.  It also removes the content if the transferEncoding is not SINGLE.
            m.setTransferEncoding(te);

            // Encode the message.
            out.markWriterIndex();
            encodeInitialLine(out, m);
            encodeHeaders(out, m);
            out.writeByte(CR);
            out.writeByte(LF);

            ByteBuf content = m.getContent();
            out.writeBytes(content, content.readerIndex(), content.readableBytes());
        } else if (msg instanceof HttpChunk) {
            HttpChunk chunk = (HttpChunk) msg;
            HttpTransferEncoding te = lastTE;
            if (te == null) {
                throw new IllegalArgumentException("HttpChunk must follow an HttpMessage.");
            }

            switch (te) {
            case SINGLE:
                throw new IllegalArgumentException(
                        "The transfer encoding of the last encoded HttpMessage is SINGLE.");
            case STREAMED: {
                ByteBuf content = chunk.getContent();
                out.writeBytes(content, content.readerIndex(), content.readableBytes());
                break;
            }
            case CHUNKED:
                if (chunk.isLast()) {
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
                    ByteBuf content = chunk.getContent();
                    int contentLength = content.readableBytes();
                    out.writeBytes(copiedBuffer(Integer.toHexString(contentLength), CharsetUtil.US_ASCII));
                    out.writeByte(CR);
                    out.writeByte(LF);
                    out.writeBytes(content, content.readerIndex(), contentLength);
                    out.writeByte(CR);
                    out.writeByte(LF);
                }
            }
        } else {
            throw new UnsupportedMessageTypeException(msg, HttpMessage.class, HttpChunk.class);
        }
    }

    private static void encodeHeaders(ByteBuf buf, HttpMessage message) {
        for (Map.Entry<String, String> h: message.getHeaders()) {
            encodeHeader(buf, h.getKey(), h.getValue());
        }
    }

    private static void encodeTrailingHeaders(ByteBuf buf, HttpChunkTrailer trailer) {
        for (Map.Entry<String, String> h: trailer.getHeaders()) {
            encodeHeader(buf, h.getKey(), h.getValue());
        }
    }

    private static void encodeHeader(ByteBuf buf, String header, String value) {
        buf.writeBytes(header.getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(COLON);
        buf.writeByte(SP);
        buf.writeBytes(value.getBytes(CharsetUtil.US_ASCII));
        buf.writeByte(CR);
        buf.writeByte(LF);
    }

    protected abstract void encodeInitialLine(ByteBuf buf, HttpMessage message) throws Exception;
}
