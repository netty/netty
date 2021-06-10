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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;

import java.util.function.BooleanSupplier;

import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpUtil.normalizeAndGetContentLength;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_MESSAGE_ERROR;
import static io.netty.incubator.codec.http3.Http3FrameValidationUtils.frameTypeUnexpected;

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

    static Http3RequestStreamValidationHandler newServerValidator(
            QpackAttributes qpackAttributes, QpackDecoder decoder, Http3RequestStreamCodecState encodeState,
            Http3RequestStreamCodecState decodeState) {
        return new Http3RequestStreamValidationHandler(true, () -> false, qpackAttributes, decoder,
                encodeState, decodeState);
    }

    static Http3RequestStreamValidationHandler newClientValidator(
            BooleanSupplier goAwayReceivedSupplier, QpackAttributes qpackAttributes, QpackDecoder decoder,
            Http3RequestStreamCodecState encodeState, Http3RequestStreamCodecState decodeState) {
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
            if (goAwayReceivedSupplier.getAsBoolean() && !encodeState.started()) {
                String type = StringUtil.simpleClassName(frame);
                ReferenceCountUtil.release(frame);
                promise.setFailure(new Http3Exception(Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "Frame of type " + type + " unexpected as we received a GOAWAY already."));
                ctx.close();
                return;
            }
            if (frame instanceof Http3HeadersFrame) {
                clientHeadRequest = HEAD.asciiName().equals(((Http3HeadersFrame) frame).headers().method());
            }
            if (frame instanceof Http3PushPromiseFrame) {
                // Only supported on the server.
                // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1
                frameTypeUnexpected(promise, frame);
                return;
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
            if (headersFrame.headers().contains(HttpHeaderNames.CONNECTION)) {
                headerUnexpected(ctx, frame, "connection header not allowed");
                return;
            }
            CharSequence value = headersFrame.headers().get(HttpHeaderNames.TE);
            if (value != null && !HttpHeaderValues.TRAILERS.equals(value)) {
                headerUnexpected(ctx, frame, "te header field contains an invalid value: " + value);
                return;
            }
            if (decodeState.receivedFinalHeaders()) {
                long length = normalizeAndGetContentLength(
                        headersFrame.headers().getAll(HttpHeaderNames.CONTENT_LENGTH), false, true);
                if (length >= 0) {
                    headersFrame.headers().setLong(HttpHeaderNames.CONTENT_LENGTH, length);
                    expectedLength = length;
                }
            }
        }

        if (frame instanceof Http3DataFrame) {
            try {
                verifyContentLength(((Http3DataFrame) frame).content().readableBytes(), false);
            } catch (Http3Exception e) {
                ReferenceCountUtil.release(frame);
                failStream(ctx, e);
                return;
            }
        }
        ctx.fireChannelRead(frame);
    }

    private void headerUnexpected(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, String msg) {
        // We should close the stream.
        // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1
        ReferenceCountUtil.release(frame);
        failStream(ctx, new Http3Exception(H3_MESSAGE_ERROR, msg));
    }

    private void failStream(ChannelHandlerContext ctx, Http3Exception cause) {
        ctx.fireExceptionCaught(cause);
        Http3CodecUtils.streamError(ctx, cause.errorCode());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
            try {
                sendStreamAbandonedIfRequired(ctx);
                verifyContentLength(0, true);
            } catch (Http3Exception e) {
                ctx.fireExceptionCaught(e);
                Http3CodecUtils.streamError(ctx, e.errorCode());
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

    // See https://tools.ietf.org/html/draft-ietf-quic-http-34#section-4.1.3
    private void verifyContentLength(int length, boolean end) throws Http3Exception {
        seenLength += length;
        if (expectedLength != -1 && (seenLength > expectedLength ||
                (!clientHeadRequest && end && seenLength != expectedLength))) {
            throw new Http3Exception(
                    H3_MESSAGE_ERROR, "Expected content-length " + expectedLength +
                    " != " + seenLength + ".");
        }
    }

    private void sendStreamAbandonedIfRequired(ChannelHandlerContext ctx) {
        if (!qpackAttributes.dynamicTableDisabled() && !decodeState.terminated()) {
            final long streamId = ((QuicStreamChannel) ctx.channel()).streamId();
            if (qpackAttributes.decoderStreamAvailable()) {
                qpackDecoder.streamAbandoned(qpackAttributes.decoderStream(), streamId);
            } else {
                qpackAttributes.whenDecoderStreamAvailable(future -> {
                    if (future.isSuccess()) {
                        qpackDecoder.streamAbandoned(qpackAttributes.decoderStream(), streamId);
                    }
                });
            }
        }
    }
}
