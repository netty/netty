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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Encodes a {@link WebSocketFrame} into a {@link ByteBuf}.
 * <p>
 * For the detailed instruction on adding add Web Socket support to your HTTP server, take a look into the
 * <tt>WebSocketServer</tt> example located in the {@code io.netty.example.http.websocket} package.
 */
@Sharable
public class WebSocket00FrameEncoder extends MessageToByteEncoder<WebSocketFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg, ByteBuf out) throws Exception {
        if (msg instanceof TextWebSocketFrame) {
            // Text frame
            ByteBuf data = msg.content();
            out.writeByte((byte) 0x00);
            out.writeBytes(data, data.readerIndex(), data.readableBytes());
            out.writeByte((byte) 0xFF);
        } else if (msg instanceof CloseWebSocketFrame) {
            // Close frame
            out.writeByte((byte) 0xFF);
            out.writeByte((byte) 0x00);
        } else {
            // Binary frame
            ByteBuf data = msg.content();
            int dataLen = data.readableBytes();
            out.ensureWritable(dataLen + 5);

            // Encode type.
            out.writeByte((byte) 0x80);

            // Encode length.
            int b1 = dataLen >>> 28 & 0x7F;
            int b2 = dataLen >>> 14 & 0x7F;
            int b3 = dataLen >>> 7 & 0x7F;
            int b4 = dataLen & 0x7F;
            if (b1 == 0) {
                if (b2 == 0) {
                    if (b3 == 0) {
                        out.writeByte(b4);
                    } else {
                        out.writeByte(b3 | 0x80);
                        out.writeByte(b4);
                    }
                } else {
                    out.writeByte(b2 | 0x80);
                    out.writeByte(b3 | 0x80);
                    out.writeByte(b4);
                }
            } else {
                out.writeByte(b1 | 0x80);
                out.writeByte(b2 | 0x80);
                out.writeByte(b3 | 0x80);
                out.writeByte(b4);
            }

            // Encode binary data.
            out.writeBytes(data, data.readerIndex(), dataLen);
        }
    }
}
