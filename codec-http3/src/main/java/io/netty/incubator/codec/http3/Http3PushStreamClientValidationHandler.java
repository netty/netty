/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;

import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.INVALID_FRAME_READ;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.sendStreamAbandonedIfRequired;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateDataFrameRead;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateHeaderFrameRead;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateOnStreamClosure;

final class Http3PushStreamClientValidationHandler
        extends Http3FrameTypeInboundValidationHandler<Http3RequestStreamFrame> {
    private final QpackAttributes qpackAttributes;
    private final QpackDecoder qpackDecoder;
    private final Http3RequestStreamCodecState decodeState;

    private long expectedLength = -1;
    private long seenLength;

    Http3PushStreamClientValidationHandler(QpackAttributes qpackAttributes, QpackDecoder qpackDecoder,
                                           Http3RequestStreamCodecState decodeState) {
        super(Http3RequestStreamFrame.class);
        this.qpackAttributes = qpackAttributes;
        this.qpackDecoder = qpackDecoder;
        this.decodeState = decodeState;
    }

    @Override
    void channelRead(ChannelHandlerContext ctx, Http3RequestStreamFrame frame) {
        if (frame instanceof Http3PushPromiseFrame) {
            ctx.fireChannelRead(frame);
            return;
        }

        if (frame instanceof Http3HeadersFrame) {
            Http3HeadersFrame headersFrame = (Http3HeadersFrame) frame;
            long maybeContentLength = validateHeaderFrameRead(headersFrame, ctx, decodeState);
            if (maybeContentLength >= 0) {
                expectedLength = maybeContentLength;
            } else if (maybeContentLength == INVALID_FRAME_READ) {
                return;
            }
        }

        if (frame instanceof Http3DataFrame) {
            final Http3DataFrame dataFrame = (Http3DataFrame) frame;
            long maybeContentLength = validateDataFrameRead(dataFrame, ctx, expectedLength, seenLength, false);
            if (maybeContentLength >= 0) {
                seenLength = maybeContentLength;
            } else if (maybeContentLength == INVALID_FRAME_READ) {
                return;
            }
        }
        ctx.fireChannelRead(frame);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
            sendStreamAbandonedIfRequired(ctx, qpackAttributes, qpackDecoder, decodeState);
            if (!validateOnStreamClosure(ctx, expectedLength, seenLength, false)) {
                return;
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public boolean isSharable() {
        // This handle keeps state so we can't share it.
        return false;
    }
}
