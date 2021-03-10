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
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.StringUtil;

import java.util.function.BooleanSupplier;

final class Http3RequestStreamValidationHandler extends Http3FrameTypeValidationHandler<Http3RequestStreamFrame> {
    private enum State {
        Initial,
        Started,
        End
    }
    private State readState = State.Initial;
    private State writeState = State.Initial;
    private final boolean server;
    private final BooleanSupplier goAwayReceivedSupplier;

    private boolean head;
    private long expectedLength = -1;
    private long seenLength;

    static Http3RequestStreamValidationHandler newServerValidator() {
        return new Http3RequestStreamValidationHandler(true, () -> false);
    }

    static Http3RequestStreamValidationHandler newClientValidator(BooleanSupplier goAwayReceivedSupplier) {
        return new Http3RequestStreamValidationHandler(false, goAwayReceivedSupplier);
    }

    private Http3RequestStreamValidationHandler(boolean server, BooleanSupplier goAwayReceivedSupplier) {
        super(Http3RequestStreamFrame.class);
        this.server = server;
        this.goAwayReceivedSupplier = goAwayReceivedSupplier;
    }

    private State checkState(boolean inbound, State state, Http3RequestStreamFrame frame) throws Http3Exception {
        switch (state) {
            case Initial:
                if (!(frame instanceof Http3HeadersFrame)) {
                    return null;
                }
                Http3HeadersFrame headersFrame = (Http3HeadersFrame) frame;
                if (inbound) {
                    if (headersFrame.headers().contains(HttpHeaderNames.CONNECTION)) {
                        // We should close the stream.
                        // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1
                        throw new Http3Exception(Http3ErrorCode.H3_MESSAGE_ERROR,
                                "connection header included");
                    }
                    CharSequence value = headersFrame.headers().get(HttpHeaderNames.TE);
                    if (value != null && !HttpHeaderValues.TRAILERS.equals(value)) {
                        // We should close the stream.
                        // See https://quicwg.org/base-drafts/draft-ietf-quic-http.html#section-4.1.1
                        throw new Http3Exception(Http3ErrorCode.H3_MESSAGE_ERROR,
                                "te header field included with invalid value: " + value);
                    }
                    long length = HttpUtil.normalizeAndGetContentLength(headersFrame.headers()
                            .getAll(HttpHeaderNames.CONTENT_LENGTH), false, true);
                    if (length != -1) {
                        headersFrame.headers().setLong(HttpHeaderNames.CONTENT_LENGTH, length);
                        expectedLength = length;
                    }
                } else if (!server) {
                    head = HttpMethod.HEAD.asciiName().equals(headersFrame.headers().method());
                }
                return State.Started;
            case Started:
                if (inbound && frame instanceof Http3DataFrame) {
                    verifyContentLength(((Http3DataFrame) frame).content().readableBytes(), false);
                }
                if (frame instanceof Http3HeadersFrame) {
                    if (inbound) {
                        verifyContentLength(0, true);
                    }
                    // trailers
                    return State.End;
                }
                return state;
            case End:
                return null;
            default:
                throw new Error();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, ChannelPromise promise) {
        if (!(frame instanceof Http3PushPromiseFrame)) {
            if (!server && writeState == State.Initial && goAwayReceivedSupplier.getAsBoolean()) {
                String type = StringUtil.simpleClassName(frame);
                ReferenceCountUtil.release(frame);
                promise.setFailure(new Http3Exception(Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "Frame of type " + type + " unexpected as we received a GOAWAY already."));
                ctx.close();
                return;
            }
            try {
                State newState = checkState(false, writeState, frame);
                if (newState == null) {
                    frameTypeUnexpected(promise, frame);
                    return;
                }
                writeState = newState;
            } catch (Http3Exception e) {
                ReferenceCountUtil.release(frame);
                promise.setFailure(e);
                ctx.close();
            }
        } else if (!server) {
            // Only supported on the server.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1
            frameTypeUnexpected(promise, frame);
            return;
        }

        ctx.write(frame, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Http3RequestStreamFrame frame) {
        if (!(frame instanceof Http3PushPromiseFrame)) {
            try {
                State newState = checkState(true, readState, frame);
                if (newState == null) {
                    frameTypeUnexpected(ctx, frame);
                    return;
                }
                readState = newState;
            } catch (Http3Exception e) {
                ReferenceCountUtil.release(frame);
                ctx.fireExceptionCaught(e);
                ctx.close();
            }
        } else if (!server) {
            // Only supported on the server.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1
            frameTypeUnexpected(ctx, frame);
            return;
        }
        ctx.fireChannelRead(frame);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
            try {
                verifyContentLength(0, true);
            } catch (Http3Exception e) {
                ctx.fireExceptionCaught(e);
                ctx.close();
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
        if (expectedLength != -1 && (seenLength > expectedLength || (!head && end && seenLength != expectedLength))) {
            throw new Http3Exception(
                    Http3ErrorCode.H3_MESSAGE_ERROR, "Expected content-length " + expectedLength +
                    " != " + seenLength + ".");
        }
    }
}
