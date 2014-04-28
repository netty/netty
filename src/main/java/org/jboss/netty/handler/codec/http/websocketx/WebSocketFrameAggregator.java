/*
 * Copyright 2013 The Netty Project
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
package org.jboss.netty.handler.codec.http.websocketx;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

/**
 * Handler that aggregate fragmented WebSocketFrame's.
 *
 * Be aware if PING/PONG/CLOSE frames are send in the middle of a fragmented {@link WebSocketFrame} they will
 * just get forwarded to the next handler in the pipeline.
 */
public class WebSocketFrameAggregator extends OneToOneDecoder {
    private final int maxFrameSize;
    private WebSocketFrame currentFrame;
    private boolean tooLongFrameFound;

    /**
     * Construct a new instance
     *
     * @param maxFrameSize      If the size of the aggregated frame exceeds this value,
     *                          a {@link TooLongFrameException} is thrown.
     */
    public WebSocketFrameAggregator(int maxFrameSize) {
        if (maxFrameSize < 1) {
            throw new IllegalArgumentException("maxFrameSize must be > 0");
        }
        this.maxFrameSize = maxFrameSize;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object message) throws Exception {
        if (!(message instanceof WebSocketFrame)) {
            return message;
        }
        WebSocketFrame msg = (WebSocketFrame) message;
        if (currentFrame == null) {
            tooLongFrameFound = false;
            if (msg.isFinalFragment()) {
                return msg;
            }
            ChannelBuffer buf = msg.getBinaryData();

            if (msg instanceof TextWebSocketFrame) {
                currentFrame = new TextWebSocketFrame(true, msg.getRsv(), buf);
            } else if (msg instanceof BinaryWebSocketFrame) {
                currentFrame = new BinaryWebSocketFrame(true, msg.getRsv(), buf);
            } else {
                throw new IllegalStateException(
                        "WebSocket frame was not of type TextWebSocketFrame or BinaryWebSocketFrame");
            }
            return null;
        }
        if (msg instanceof ContinuationWebSocketFrame) {
            if (tooLongFrameFound) {
                if (msg.isFinalFragment()) {
                    currentFrame = null;
                }
                return null;
            }
            ChannelBuffer content = currentFrame.getBinaryData();
            if (content.readableBytes() > maxFrameSize - msg.getBinaryData().readableBytes()) {
                tooLongFrameFound = true;
                throw new TooLongFrameException(
                        "WebSocketFrame length exceeded " + content +
                                " bytes.");
            }
            currentFrame.setBinaryData(ChannelBuffers.wrappedBuffer(content, msg.getBinaryData()));

            if (msg.isFinalFragment()) {
                WebSocketFrame currentFrame = this.currentFrame;
                this.currentFrame = null;
                return currentFrame;
            } else {
                return null;
            }
        }
        // It is possible to receive CLOSE/PING/PONG frames during fragmented frames so just pass them to the next
        // handler in the chain
        return msg;
    }
}
