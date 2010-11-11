/*
 * Copyright 2010 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.http.websocket;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.handler.codec.replay.VoidEnum;

/**
 * Decodes {@link ChannelBuffer}s into {@link WebSocketFrame}s.
 * <p>
 * For the detailed instruction on adding add Web Socket support to your HTTP
 * server, take a look into the <tt>WebSocketServer</tt> example located in the
 * {@code org.jboss.netty.example.http.websocket} package.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Mike Heath (mheath@apache.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2342 $, $Date: 2010-07-07 14:07:39 +0900 (Wed, 07 Jul 2010) $
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.codec.http.websocket.WebSocketFrame
 */
public class WebSocketFrameDecoder extends ReplayingDecoder<VoidEnum> {

    public static final int DEFAULT_MAX_FRAME_SIZE = 16384;

    private final int maxFrameSize;
    private boolean receivedClosingHandshake;

    public WebSocketFrameDecoder() {
        this(DEFAULT_MAX_FRAME_SIZE);
    }

    /**
     * Creates a new instance of {@code WebSocketFrameDecoder} with the specified {@code maxFrameSize}.  If the client
     * sends a frame size larger than {@code maxFrameSize}, the channel will be closed.
     *
     * @param maxFrameSize  the maximum frame size to decode
     */
    public WebSocketFrameDecoder(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, VoidEnum state) throws Exception {

        // Discard all data received if closing handshake was received before.
        if (receivedClosingHandshake) {
            buffer.skipBytes(actualReadableBytes());
            return null;
        }

        // Decode a frame otherwise.
        byte type = buffer.readByte();
        if ((type & 0x80) == 0x80) {
            // If the MSB on type is set, decode the frame length
            return decodeBinaryFrame(type, buffer);
        } else {
            // Decode a 0xff terminated UTF-8 string
            return decodeTextFrame(type, buffer);
        }
    }

    private WebSocketFrame decodeBinaryFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
        long frameSize = 0;
        int lengthFieldSize = 0;
        byte b;
        do {
            b = buffer.readByte();
            frameSize <<= 7;
            frameSize |= b & 0x7f;
            if (frameSize > maxFrameSize) {
                throw new TooLongFrameException();
            }
            lengthFieldSize ++;
            if (lengthFieldSize > 8) {
                // Perhaps a malicious peer?
                throw new TooLongFrameException();
            }
        } while ((b & 0x80) == 0x80);

        if (type == 0xFF && frameSize == 0) {
            receivedClosingHandshake = true;
        }

        return new DefaultWebSocketFrame(
                type, buffer.readBytes((int) frameSize));
    }

    private WebSocketFrame decodeTextFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
        int ridx = buffer.readerIndex();
        int rbytes = actualReadableBytes();
        int delimPos = buffer.indexOf(ridx, ridx + rbytes, (byte) 0xFF);
        if (delimPos == -1) {
            // Frame delimiter (0xFF) not found
            if (rbytes > maxFrameSize) {
                // Frame length exceeded the maximum
                throw new TooLongFrameException();
            } else {
                // Wait until more data is received
                return null;
            }
        }

        int frameSize = delimPos - ridx;
        if (frameSize > maxFrameSize) {
            throw new TooLongFrameException();
        }

        ChannelBuffer binaryData = buffer.readBytes(frameSize);
        buffer.skipBytes(1);
        return new DefaultWebSocketFrame(type, binaryData);
    }
}
