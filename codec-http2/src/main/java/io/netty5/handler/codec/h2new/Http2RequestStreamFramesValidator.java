/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.h2new;

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;

/**
 * A {@link ChannelHandler} that validates frames exchanged on a stream according to
 * <a href="https://httpwg.org/specs/rfc7540.html#HTTPLayer">HTTP Request/Response exchange rules</a>. This handler
 * should be added only to a {@link Http2StreamChannel}.
 */
final class Http2RequestStreamFramesValidator extends AbstractHttp2StreamFramesValidator {
    private final DefaultHttp2RequestStreamCodecState remoteState = new DefaultHttp2RequestStreamCodecState();
    private final DefaultHttp2RequestStreamCodecState localState = new DefaultHttp2RequestStreamCodecState();
    private final boolean isServer;

    Http2RequestStreamFramesValidator(boolean isServer) {
        this.isServer = isServer;
    }

    @Override
    boolean validateFrameRead(ChannelHandlerContext ctx, Http2Frame frame) {
        if (!(frame instanceof Http2RequestStreamFrame)) {
            connectionCloseOnUnexpectedFrame(ctx.channel().parent(), frame);
            return false;
        }
        // TODO: Validate message exchange semantics: https://httpwg.org/specs/rfc7540.html#HTTPLayer
        // TODO: Validate frames as per stream state
        Http2RequestStreamFrame requestStreamFrame = (Http2RequestStreamFrame) frame;
        if (!remoteState.evaluateFrame(requestStreamFrame)) {
            connectionCloseOnUnexpectedFrame(ctx.channel().parent(), frame);
            return false;
        }
        return true;
    }

    @Override
    void validateFrameWritten(ChannelHandlerContext ctx, Http2Frame frame) {
        if (frame instanceof Http2RequestStreamFrame) {
            // TODO: Validate message exchange semantics: https://httpwg.org/specs/rfc7540.html#HTTPLayer
            Http2RequestStreamFrame requestStreamFrame = (Http2RequestStreamFrame) frame;
            localState.evaluateFrame(requestStreamFrame);
        }
    }

    DefaultHttp2RequestStreamCodecState remoteState() {
        return remoteState;
    }

    DefaultHttp2RequestStreamCodecState localState() {
        return localState;
    }
}
