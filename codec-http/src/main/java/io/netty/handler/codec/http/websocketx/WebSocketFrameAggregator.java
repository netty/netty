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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

/**
 * Handler that aggregate fragmented WebSocketFrame's.
 *
 * Be aware if PING/PONG/CLOSE frames are send in the middle of a fragmented {@link WebSocketFrame} they will
 * just get forwarded to the next handler in the pipeline.
 */
public class WebSocketFrameAggregator extends MessageToMessageDecoder<WebSocketFrame> {
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
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        if (currentFrame == null) {
            tooLongFrameFound = false;
            if (msg.isFinalFragment()) {
                out.add(msg.retain());
                return;
            }
            ByteBuf buf = ctx.alloc().compositeBuffer().addComponent(msg.content().retain());
            buf.writerIndex(buf.writerIndex() + msg.content().readableBytes());

            if (msg instanceof TextWebSocketFrame) {
                currentFrame = new TextWebSocketFrame(true, msg.rsv(), buf);
            } else if (msg instanceof BinaryWebSocketFrame) {
                currentFrame = new BinaryWebSocketFrame(true, msg.rsv(), buf);
            } else {
                buf.release();
                throw new IllegalStateException(
                        "WebSocket frame was not of type TextWebSocketFrame or BinaryWebSocketFrame");
            }
            return;
        }
        if (msg instanceof ContinuationWebSocketFrame) {
            if (tooLongFrameFound) {
                if (msg.isFinalFragment()) {
                    currentFrame = null;
                }
                return;
            }
            CompositeByteBuf content = (CompositeByteBuf) currentFrame.content();
            if (content.readableBytes() > maxFrameSize - msg.content().readableBytes()) {
                // release the current frame
                currentFrame.release();
                tooLongFrameFound = true;
                throw new TooLongFrameException(
                        "WebSocketFrame length exceeded " + content +
                                " bytes.");
            }
            content.addComponent(msg.content().retain());
            content.writerIndex(content.writerIndex() + msg.content().readableBytes());

            if (msg.isFinalFragment()) {
                WebSocketFrame currentFrame = this.currentFrame;
                this.currentFrame = null;
                out.add(currentFrame);
                return;
            } else {
                return;
            }
        }
        // It is possible to receive CLOSE/PING/PONG frames during fragmented frames so just pass them to the next
        // handler in the chain
        out.add(msg.retain());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        // release current frame if it is not null as it may be a left-over
        if (currentFrame != null) {
            currentFrame.release();
            currentFrame = null;
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        // release current frame if it is not null as it may be a left-over as there is not much more we can do in
        // this case
        if (currentFrame != null) {
            currentFrame.release();
            currentFrame = null;
        }
    }
}
