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

import static io.netty5.util.internal.StringUtil.simpleClassName;

/**
 * A {@link ChannelHandler} that validates frames sent/received on stream ID {@code 0}. This handler
 * should be added only to a {@link Http2StreamChannel}.
 */
final class Http2ControlStreamFramesValidator extends AbstractHttp2StreamFramesValidator {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (!(ctx.channel() instanceof Http2StreamChannel)) {
            throw new IllegalArgumentException(simpleClassName(Http2ControlStreamFramesValidator.class.getName()) +
                    " should only be added to an " + simpleClassName(Http2StreamChannel.class) + ", found: " +
                    simpleClassName(ctx.channel().getClass().getName()));
        }
    }

    @Override
    boolean validateFrameRead(ChannelHandlerContext ctx, Http2Frame frame) {
        if (!(frame instanceof Http2ControlStreamFrame)) {
            connectionCloseOnUnexpectedFrame(ctx.channel().parent(), frame);
            return false;
        }
        // TODO: Verify control stream frame ordering (settings received)
        return true;
    }

    @Override
    void validateFrameWritten(ChannelHandlerContext ctx, Http2Frame frame) {
    }
}
