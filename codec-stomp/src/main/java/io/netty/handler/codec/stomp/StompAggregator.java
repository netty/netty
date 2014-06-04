/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import java.util.List;

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

/**
 * A {@link io.netty.channel.ChannelHandler} that aggregates an {@link StompFrame}
 * and its following {@link StompContent}s into a single {@link StompFrame} with
 * no following {@link StompContent}s.  It is useful when you don't want to take
 * care of STOMP frames whose content is 'chunked'.  Insert this
 * handler after {@link StompDecoder} in the {@link io.netty.channel.ChannelPipeline}:
 */
public class StompAggregator extends MessageToMessageDecoder<StompObject> {
    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;

    private final int maxContentLength;
    private FullStompFrame currentFrame;
    private boolean tooLongFrameFound;
    private volatile boolean handlerAdded;

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public StompAggregator(int maxContentLength) {
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " +
                            maxContentLength);
        }
        this.maxContentLength = maxContentLength;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                            " (expected: >= 2)");
        }

        if (!handlerAdded) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, StompObject msg, List<Object> out) throws Exception {
        FullStompFrame currentFrame = this.currentFrame;
        if (msg instanceof StompFrame) {
            assert currentFrame == null;
            StompFrame frame = (StompFrame) msg;
            this.currentFrame = currentFrame = new DefaultFullStompFrame(frame.command(),
                Unpooled.compositeBuffer(maxCumulationBufferComponents));
            currentFrame.headers().set(frame.headers());
        } else if (msg instanceof StompContent) {
            if (tooLongFrameFound) {
                if (msg instanceof LastStompContent) {
                    this.currentFrame = null;
                }
                return;
            }
            assert currentFrame != null;
            StompContent chunk = (StompContent) msg;
            CompositeByteBuf contentBuf = (CompositeByteBuf) currentFrame.content();
            if (contentBuf.readableBytes() > maxContentLength - chunk.content().readableBytes()) {
                tooLongFrameFound = true;
                currentFrame.release();
                this.currentFrame = null;
                throw new TooLongFrameException(
                    "STOMP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            contentBuf.addComponent(chunk.retain().content());
            contentBuf.writerIndex(contentBuf.writerIndex() + chunk.content().readableBytes());
            if (chunk instanceof LastStompContent) {
                out.add(currentFrame);
                this.currentFrame = null;
            }
        } else {
            throw new IllegalArgumentException("received unsupported object type " + msg);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.handlerAdded = true;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (currentFrame != null) {
            currentFrame.release();
            currentFrame = null;
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        this.handlerAdded = false;
        if (currentFrame != null) {
            currentFrame.release();
            currentFrame = null;
        }
    }
}
