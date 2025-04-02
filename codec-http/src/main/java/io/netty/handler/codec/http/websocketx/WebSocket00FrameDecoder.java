/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.ObjectUtil;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.readBytes;

/**
 * Decodes {@link ByteBuf}s into {@link WebSocketFrame}s.
 * <p>
 * For the detailed instruction on adding add Web Socket support to your HTTP server, take a look into the
 * <tt>WebSocketServer</tt> example located in the {@code io.netty.example.http.websocket} package.
 */
public class WebSocket00FrameDecoder extends ReplayingDecoder<Void> implements WebSocketFrameDecoder {

    static final int DEFAULT_MAX_FRAME_SIZE = 16384;

    private final long maxFrameSize;
    private boolean receivedClosingHandshake;

    public WebSocket00FrameDecoder() {
        this(DEFAULT_MAX_FRAME_SIZE);
    }

    /**
     * Creates a new instance of {@code WebSocketFrameDecoder} with the specified {@code maxFrameSize}. If the client
     * sends a frame size larger than {@code maxFrameSize}, the channel will be closed.
     *
     * @param maxFrameSize
     *            the maximum frame size to decode
     */
    public WebSocket00FrameDecoder(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Creates a new instance of {@code WebSocketFrameDecoder} with the specified {@code maxFrameSize}. If the client
     * sends a frame size larger than {@code maxFrameSize}, the channel will be closed.
     *
     * @param decoderConfig
     *            Frames decoder configuration.
     */
    public WebSocket00FrameDecoder(WebSocketDecoderConfig decoderConfig) {
        this.maxFrameSize = ObjectUtil.checkNotNull(decoderConfig, "decoderConfig").maxFramePayloadLength();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Discard all data received if closing handshake was received before.
        if (receivedClosingHandshake) {
            in.skipBytes(actualReadableBytes());
            return;
        }

        // Decode a frame otherwise.
        byte type = in.readByte();
        WebSocketFrame frame;
        if ((type & 0x80) == 0x80) {
            // If the MSB on type is set, decode the frame length
            frame = decodeBinaryFrame(ctx, type, in);
        } else {
            // Decode a 0xff terminated UTF-8 string
            frame = decodeTextFrame(ctx, in);
        }

        if (frame != null) {
            out.add(frame);
        }
    }

    private WebSocketFrame decodeBinaryFrame(ChannelHandlerContext ctx, byte type, ByteBuf buffer) {
        long frameSize = 0;
        int lengthFieldSize = 0;
        byte b;
        do {
            b = buffer.readByte();
            frameSize <<= 7;
            frameSize |= b & 0x7f;
            if (frameSize > maxFrameSize) {
                throw new TooLongFrameException("frame length exceeds " + maxFrameSize + ": " + frameSize);
            }
            lengthFieldSize++;
            if (lengthFieldSize > 8) {
                // Perhaps a malicious peer?
                throw new TooLongFrameException("frame length field size exceeds 8: " + lengthFieldSize);
            }
        } while ((b & 0x80) == 0x80);

        if (type == (byte) 0xFF && frameSize == 0) {
            receivedClosingHandshake = true;
            return new CloseWebSocketFrame(true, 0, ctx.alloc().buffer(0));
        }
        ByteBuf payload = readBytes(ctx.alloc(), buffer, (int) frameSize);
        return new BinaryWebSocketFrame(payload);
    }

    private WebSocketFrame decodeTextFrame(ChannelHandlerContext ctx, ByteBuf buffer) {
        int ridx = buffer.readerIndex();
        int rbytes = actualReadableBytes();
        int delimPos = buffer.indexOf(ridx, ridx + rbytes, (byte) 0xFF);
        if (delimPos == -1) {
            // Frame delimiter (0xFF) not found
            if (rbytes > maxFrameSize) {
                // Frame length exceeded the maximum
                throw new TooLongFrameException("frame length exceeds " + maxFrameSize + ": " + rbytes);
            } else {
                // Wait until more data is received
                return null;
            }
        }

        int frameSize = delimPos - ridx;
        if (frameSize > maxFrameSize) {
            throw new TooLongFrameException("frame length exceeds " + maxFrameSize + ": " + frameSize);
        }

        ByteBuf binaryData = readBytes(ctx.alloc(), buffer, frameSize);
        buffer.skipBytes(1);

        int ffDelimPos = binaryData.indexOf(binaryData.readerIndex(), binaryData.writerIndex(), (byte) 0xFF);
        if (ffDelimPos >= 0) {
            binaryData.release();
            throw new IllegalArgumentException("a text frame should not contain 0xFF.");
        }

        return new TextWebSocketFrame(binaryData);
    }
}
