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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.MessageAggregatorNew;
import io.netty5.handler.codec.TooLongFrameException;

/**
 * Handler that aggregate fragmented WebSocketFrame's.
 *
 * Be aware if PING/PONG/CLOSE frames are send in the middle of a fragmented {@link WebSocketFrame} they will
 * just get forwarded to the next handler in the pipeline.
 */
public class WebSocketFrameAggregator
        extends MessageAggregatorNew<WebSocketFrame, WebSocketFrame, ContinuationWebSocketFrame, WebSocketFrame> {

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
    protected WebSocketFrame tryStartMessage(Object msg) {
        return isStartMessage(msg) ? (WebSocketFrame) msg : null;
    }

    @Override
    protected ContinuationWebSocketFrame tryContentMessage(Object msg) {
        return isContentMessage(msg) ? (ContinuationWebSocketFrame) msg : null;
    }

    @Override
    protected boolean isLastContentMessage(ContinuationWebSocketFrame msg) {
        return isContentMessage(msg) && msg.isFinalFragment();
    }

    @Override
    protected boolean isAggregated(Object msg) throws Exception {
        if (!(msg instanceof WebSocketFrame)) {
            return false;
        }
        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame.isFinalFragment()) {
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
    protected int lengthForContent(ContinuationWebSocketFrame msg) {
        return msg.binaryData().readableBytes();
    }

    @Override
    protected int lengthForAggregation(WebSocketFrame msg) {
        return msg.binaryData().readableBytes();
    }

    @Override
    protected WebSocketFrame beginAggregation(BufferAllocator allocator, WebSocketFrame start) {
        if (start instanceof TextWebSocketFrame) {
            final CompositeBuffer content = CompositeBuffer.compose(allocator, start.binaryData().send());
            return new TextWebSocketFrame(true, start.rsv(), content);
        }

        if (start instanceof BinaryWebSocketFrame) {
            final CompositeBuffer content = CompositeBuffer.compose(allocator, start.binaryData().send());
            return new BinaryWebSocketFrame(true, start.rsv(), content);
        }

        // Should not reach here.
        throw new Error();
    }

    @Override
    protected void aggregate(BufferAllocator allocator, WebSocketFrame aggregated, ContinuationWebSocketFrame content)
            throws Exception {
        final CompositeBuffer payload = (CompositeBuffer) aggregated.binaryData();
        payload.extendWith(content.binaryData().send());
    }

    private static boolean isStartMessage(Object msg) {
        return msg instanceof TextWebSocketFrame || msg instanceof BinaryWebSocketFrame;
    }

    private static boolean isContentMessage(Object msg) {
        return msg instanceof ContinuationWebSocketFrame;
    }
}
