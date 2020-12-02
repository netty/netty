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

final class Http3RequestStreamValidationHandler extends Http3FrameTypeValidationHandler<Http3RequestStreamFrame> {
    private enum State {
        Initial,
        Started,
        End
    }
    private State readState = State.Initial;
    private State writeState = State.Initial;
    private final boolean server;

    Http3RequestStreamValidationHandler(boolean server) {
        super(Http3RequestStreamFrame.class);
        this.server = server;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, ChannelPromise promise) {
        if (!(frame instanceof Http3PushPromiseFrame)) {
            switch (writeState) {
                case Initial:
                    if (!(frame instanceof Http3HeadersFrame)) {
                        frameTypeUnexpected(promise, frame);
                        return;
                    }
                    writeState = State.Started;
                    break;
                case Started:
                    if (frame instanceof Http3HeadersFrame) {
                        // trailers
                        writeState = State.End;
                    }
                    break;
                case End:
                    frameTypeUnexpected(promise, frame);
                    return;
                default:
                    throw new Error();
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
            switch (readState) {
                case Initial:
                    if (!(frame instanceof Http3HeadersFrame)) {
                        frameTypeUnexpected(ctx, frame);
                        return;
                    }
                    readState = State.Started;
                    break;
                case Started:
                    if (frame instanceof Http3HeadersFrame) {
                        // trailers
                        readState = State.End;
                    }
                    break;
                case End:
                    frameTypeUnexpected(ctx, frame);
                    return;
                default:
                    throw new Error();
            }
        } else if (!server) {
            // Only supported on the server.
            // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-4.1
            frameTypeUnexpected(ctx, frame);
            return;
        }
        ctx.fireChannelRead(frame);
    }
}
