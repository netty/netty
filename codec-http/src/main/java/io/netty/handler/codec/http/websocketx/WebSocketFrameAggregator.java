/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageAggregator;
import io.netty.handler.codec.TooLongFrameException;

/**
 * Handler that aggregate fragmented WebSocketFrame's.
 *
 * Be aware if PING/PONG/CLOSE frames are send in the middle of a fragmented {@link WebSocketFrame} they will
 * just get forwarded to the next handler in the pipeline.
 */
public class WebSocketFrameAggregator
        extends MessageAggregator<WebSocketFrame, WebSocketFrame, ContinuationWebSocketFrame, WebSocketFrame> {

    /**
     * Creates a new instance
     *
     * @param maxContentLength If the size of the aggregated frame exceeds this value,
     *                         a {@link TooLongFrameException} is thrown.
     */
    public WebSocketFrameAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected boolean isStartMessage(WebSocketFrame msg) throws Exception {
        return msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame;
    }

    @Override
    protected boolean isContentMessage(WebSocketFrame msg) throws Exception {
        return msg instanceof ContinuationWebSocketFrame;
    }

    @Override
    protected boolean isLastContentMessage(ContinuationWebSocketFrame msg) throws Exception {
        return isContentMessage(msg) && msg.isFinalFragment();
    }

    @Override
    protected boolean isAggregated(WebSocketFrame msg) throws Exception {
        if (msg.isFinalFragment()) {
            return !isContentMessage(msg);
        }

        return !isStartMessage(msg) && !isContentMessage(msg);
    }

    @Override
    protected boolean isContentLengthInvalid(WebSocketFrame start, int maxContentLength) {
        return false;
    }

    @Override
    protected Object newContinueResponse(WebSocketFrame start, int maxContentLength, ChannelPipeline pipeline) {
        return null;
    }

    @Override
    protected boolean closeAfterContinueResponse(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean ignoreContentAfterContinueResponse(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected WebSocketFrame beginAggregation(WebSocketFrame start, ByteBuf content) throws Exception {
        if (start instanceof TextWebSocketFrame) {
            return new TextWebSocketFrame(true, start.rsv(), content);
        }

        if (start instanceof BinaryWebSocketFrame) {
            return new BinaryWebSocketFrame(true, start.rsv(), content);
        }

        // Should not reach here.
        throw new Error();
    }
}
