/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;

import java.util.function.BooleanSupplier;

import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.incubator.codec.http3.Http3FrameValidationUtils.frameTypeUnexpected;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.INVALID_FRAME_READ;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.sendStreamAbandonedIfRequired;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateClientWrite;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateDataFrameRead;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateHeaderFrameRead;
import static io.netty.incubator.codec.http3.Http3RequestStreamValidationUtils.validateOnStreamClosure;

final class Http3RequestStreamValidationHandler extends Http3FrameTypeDuplexValidationHandler<Http3RequestStreamFrame> {
    private final boolean server;
    private final BooleanSupplier goAwayReceivedSupplier;
    private final QpackAttributes qpackAttributes;
    private final QpackDecoder qpackDecoder;
    private final Http3RequestStreamCodecState decodeState;
    private final Http3RequestStreamCodecState encodeState;

    private boolean clientHeadRequest;
    private long expectedLength = -1;
    private long seenLength;

    static ChannelHandler newServerValidator(QpackAttributes qpackAttributes, QpackDecoder decoder,
                                             Http3RequestStreamCodecState encodeState,
                                             Http3RequestStreamCodecState decodeState) {
        return new Http3RequestStreamValidationHandler(true, () -> false, qpackAttributes, decoder,
                encodeState, decodeState);
    }

    static ChannelHandler newClientValidator(BooleanSupplier goAwayReceivedSupplier, QpackAttributes qpackAttributes,
                                             QpackDecoder decoder, Http3RequestStreamCodecState encodeState,
                                             Http3RequestStreamCodecState decodeState) {
        return new Http3RequestStreamValidationHandler(false, goAwayReceivedSupplier, qpackAttributes, decoder,
                encodeState, decodeState);
    }

    private Http3RequestStreamValidationHandler(boolean server, BooleanSupplier goAwayReceivedSupplier,
                                                QpackAttributes qpackAttributes, QpackDecoder qpackDecoder,
                                                Http3RequestStreamCodecState encodeState,
                                                Http3RequestStreamCodecState decodeState) {
        super(Http3RequestStreamFrame.class);
        this.server = server;
        this.goAwayReceivedSupplier = goAwayReceivedSupplier;
        this.qpackAttributes = qpackAttributes;
        this.qpackDecoder = qpackDecoder;
        this.decodeState = decodeState;
        this.encodeState = encodeState;
    }

    @Override
    void write(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, ChannelPromise promise) {
        if (!server) {
            if (!validateClientWrite(frame, promise, ctx, goAwayReceivedSupplier, encodeState)) {
                return;
            }
            if (frame instanceof Http3HeadersFrame) {
                clientHeadRequest = HEAD.asciiName().equals(((Http3HeadersFrame) frame).headers().method());
            }
        }
        ctx.write(frame, promise);
    }

    @Override
    void channelRead(ChannelHandlerContext ctx, Http3RequestStreamFrame frame) {
        if (frame instanceof Http3PushPromiseFrame) {
            if (server) {
                // Server should not receive a push promise
                // https://quicwg.org/base-drafts/draft-ietf-quic-http.html#name-push_promise
                frameTypeUnexpected(ctx, frame);
            } else {
                ctx.fireChannelRead(frame);
            }
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
            long maybeContentLength = validateDataFrameRead(dataFrame, ctx, expectedLength, seenLength,
                    clientHeadRequest);
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
            if (!validateOnStreamClosure(ctx, expectedLength, seenLength, clientHeadRequest)) {
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
