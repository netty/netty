/*
 * Copyright 2012 The Netty Project
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
// (BSD License: https://www.opensource.org/licenses/bsd-license)
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

package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageEncoder;
import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p>
 * Encodes a web socket frame into wire protocol version 13 format. V13 is essentially the same as V8.
 * </p>
 */
public class WebSocket13FrameEncoder extends MessageToMessageEncoder<WebSocketFrame> implements WebSocketFrameEncoder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocket13FrameEncoder.class);
    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;
    /**
     * The size threshold for gathering writes. Non-Masked messages bigger than this size will be be sent fragmented as
     * a header and a content ByteBuf whereas messages smaller than the size will be merged into a single buffer and
     * sent at once.<br>
     * Masked messages will always be sent at once.
     */
    private static final int GATHERING_WRITE_THRESHOLD = 1024;
    private final boolean maskPayload;

    /**
     * Constructor
     *
     * @param maskPayload
     *            Web socket clients must set this to true to mask payload. Server implementations must set this to
     *            false.
     */
    public WebSocket13FrameEncoder(boolean maskPayload) {
        this.maskPayload = maskPayload;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        final Buffer data = msg.binaryData();
        byte[] mask;

        byte opcode;
        if (msg instanceof TextWebSocketFrame) {
            opcode = OPCODE_TEXT;
        } else if (msg instanceof PingWebSocketFrame) {
            opcode = OPCODE_PING;
        } else if (msg instanceof PongWebSocketFrame) {
            opcode = OPCODE_PONG;
        } else if (msg instanceof CloseWebSocketFrame) {
            opcode = OPCODE_CLOSE;
        } else if (msg instanceof BinaryWebSocketFrame) {
            opcode = OPCODE_BINARY;
        } else if (msg instanceof ContinuationWebSocketFrame) {
            opcode = OPCODE_CONT;
        } else {
            throw new UnsupportedOperationException("Cannot encode frame of type: " + msg.getClass().getName());
        }

        int length = data.readableBytes();

        if (logger.isTraceEnabled()) {
            logger.trace("Encoding WebSocket Frame opCode={} length={}", opcode, length);
        }

        int b0 = 0;
        if (msg.isFinalFragment()) {
            b0 |= 1 << 7;
        }
        b0 |= msg.rsv() % 8 << 4;
        b0 |= opcode % 128;

        if (opcode == OPCODE_PING && length > 125) {
            throw new TooLongFrameException("invalid payload for PING (payload length must be <= 125, was "
                    + length);
        }

        Buffer buf = null;
        try {
            int maskLength = maskPayload ? 4 : 0;
            if (length <= 125) {
                int size = 2 + maskLength;
                if (maskPayload || length <= GATHERING_WRITE_THRESHOLD) {
                    size += length;
                }
                buf = ctx.bufferAllocator().allocate(size);
                buf.writeByte((byte) b0);
                byte b = (byte) (maskPayload ? 0x80 | (byte) length : (byte) length);
                buf.writeByte(b);
            } else if (length <= 0xFFFF) {
                int size = 4 + maskLength;
                if (maskPayload || length <= GATHERING_WRITE_THRESHOLD) {
                    size += length;
                }
                buf = ctx.bufferAllocator().allocate(size);
                buf.writeByte((byte) b0);
                buf.writeByte((byte) (maskPayload ? 0xFE : 126));
                buf.writeByte((byte) (length >>> 8 & 0xFF));
                buf.writeByte((byte) (length & 0xFF));
            } else {
                int size = 10 + maskLength;
                if (maskPayload || length <= GATHERING_WRITE_THRESHOLD) {
                    size += length;
                }
                buf = ctx.bufferAllocator().allocate(size);
                buf.writeByte((byte) b0);
                buf.writeByte((byte) (maskPayload ? 0xFF : 127));
                buf.writeLong(length);
            }

            // Write payload
            if (maskPayload) {
                mask = new byte[4];
                ThreadLocalRandom.current().nextBytes(mask);
                buf.writeBytes(mask);

                int counter = 0;
                int i = data.readerOffset();
                int end = data.writerOffset();

                // Remark: & 0xFF is necessary because Java will do signed expansion from
                // byte to int which we don't want.
                int intMask = (mask[0] & 0xFF) << 24
                              | (mask[1] & 0xFF) << 16
                              | (mask[2] & 0xFF) << 8
                              | mask[3] & 0xFF;

                for (; i + 3 < end; i += 4) {
                    int intData = data.getInt(i);
                    buf.writeInt(intData ^ intMask);
                }
                for (; i < end; i++) {
                    byte byteData = data.getByte(i);
                    buf.writeByte((byte) (byteData ^ mask[counter++ % 4]));
                }
                out.add(buf);
            } else {
                if (buf.writableBytes() >= data.readableBytes()) {
                    // merge buffers as this is cheaper then a gathering write if the payload is small enough
                    buf.writeBytes(data);
                    data.close();
                    out.add(buf);
                } else {
                    out.add(buf);
                    out.add(data);
                }
            }
        } catch (Throwable t) {
            if (buf != null) {
                buf.close();
            }
            throw t;
        }
    }
}
