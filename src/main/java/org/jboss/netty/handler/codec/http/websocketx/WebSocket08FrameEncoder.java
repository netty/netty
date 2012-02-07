/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
// (BSD License: http://www.opensource.org/licenses/bsd-license)
//
// Copyright (c) 2011, Joe Walnes and contributors
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the
// following conditions are met:
//
// * Redistributions of source code must retain the above
// copyright notice, this list of conditions and the
// following disclaimer.
//
// * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the
// following disclaimer in the documentation and/or other
// materials provided with the distribution.
//
// * Neither the name of the Webbit nor the names of
// its contributors may be used to endorse or promote products
// derived from this software without specific prior written
// permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
// GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
// OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.jboss.netty.handler.codec.http.websocketx;

import java.nio.ByteBuffer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * <p>
 * Encodes a web socket frame into wire protocol version 8 format. This code was forked from <a
 * href="https://github.com/joewalnes/webbit">webbit</a> and modified.
 * </p>
 */
public class WebSocket08FrameEncoder extends OneToOneEncoder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocket08FrameEncoder.class);

    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;

    private final boolean maskPayload;

    /**
     * Constructor
     * 
     * @param maskPayload
     *            Web socket clients must set this to true to mask payload. Server implementations must set this to
     *            false.
     */
    public WebSocket08FrameEncoder(boolean maskPayload) {
        this.maskPayload = maskPayload;
    }

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {

        byte[] mask;

        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            ChannelBuffer data = frame.getBinaryData();
            if (data == null) {
                data = ChannelBuffers.EMPTY_BUFFER;
            }

            byte opcode;
            if (frame instanceof TextWebSocketFrame) {
                opcode = OPCODE_TEXT;
            } else if (frame instanceof PingWebSocketFrame) {
                opcode = OPCODE_PING;
            } else if (frame instanceof PongWebSocketFrame) {
                opcode = OPCODE_PONG;
            } else if (frame instanceof CloseWebSocketFrame) {
                opcode = OPCODE_CLOSE;
            } else if (frame instanceof BinaryWebSocketFrame) {
                opcode = OPCODE_BINARY;
            } else if (frame instanceof ContinuationWebSocketFrame) {
                opcode = OPCODE_CONT;
            } else {
                throw new UnsupportedOperationException("Cannot encode frame of type: " + frame.getClass().getName());
            }

            int length = data.readableBytes();

            if (logger.isDebugEnabled()) {
                logger.debug("Encoding WebSocket Frame opCode=" + opcode + " length=" + length);
            }

            int b0 = 0;
            if (frame.isFinalFragment()) {
                b0 |= 1 << 7;
            }
            b0 |= frame.getRsv() % 8 << 4;
            b0 |= opcode % 128;

            ChannelBuffer header;
            ChannelBuffer body;

            if (opcode == OPCODE_PING && length > 125) {
                throw new TooLongFrameException("invalid payload for PING (payload length must be <= 125, was "
                        + length);
            }

            int maskLength = maskPayload ? 4 : 0;
            if (length <= 125) {
                header = ChannelBuffers.buffer(2 + maskLength);
                header.writeByte(b0);
                byte b = (byte) (maskPayload ? 0x80 | (byte) length : (byte) length);
                header.writeByte(b);
            } else if (length <= 0xFFFF) {
                header = ChannelBuffers.buffer(4 + maskLength);
                header.writeByte(b0);
                header.writeByte(maskPayload ? 0xFE : 126);
                header.writeByte(length >>> 8 & 0xFF);
                header.writeByte(length & 0xFF);
            } else {
                header = ChannelBuffers.buffer(10 + maskLength);
                header.writeByte(b0);
                header.writeByte(maskPayload ? 0xFF : 127);
                header.writeLong(length);
            }

            // Write payload
            if (maskPayload) {
                Integer random = (int) (Math.random() * Integer.MAX_VALUE);
                mask = ByteBuffer.allocate(4).putInt(random).array();
                header.writeBytes(mask);

                body = ChannelBuffers.buffer(length);
                int counter = 0;
                while (data.readableBytes() > 0) {
                    byte byteData = data.readByte();
                    body.writeByte(byteData ^ mask[+counter++ % 4]);
                }
            } else {
                body = data;
            }
            return ChannelBuffers.wrappedBuffer(header, body);
        }

        // If not websocket, then just return the message
        return msg;
    }

}
