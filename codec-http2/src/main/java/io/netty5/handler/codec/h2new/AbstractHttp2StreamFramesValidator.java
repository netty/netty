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

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;

import static io.netty5.handler.codec.h2new.GoAwayManager.ProtocolErrorEvent.PROTOCOL_ERROR_EVENT_NO_DEBUG_DATA;
import static io.netty5.util.internal.logging.InternalLoggerFactory.getInstance;

/**
 * A {@link ChannelHandler} that validates frames exchanged on a stream.
 */
abstract class AbstractHttp2StreamFramesValidator extends ChannelHandlerAdapter {
    private static final InternalLogger logger = getInstance(AbstractHttp2StreamFramesValidator.class);

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame) || validateFrameRead(ctx, (Http2Frame) msg)) {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public final Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Http2Frame) {
            validateFrameWritten(ctx, (Http2Frame) msg);
        }
        return ctx.write(msg);
    }

    /**
     * Validates whether the passed {@link Http2Frame} is valid when read. If the frame is invalid, this method should
     * take appropriate action to channel and {@link Http2Frame} dispose the frame. Only valid frames will be passed
     * further in the pipeline.
     *
     * @param ctx {@link ChannelHandlerContext} for this handler.
     * @param frame to validate.
     * @return {@code true} if the frame is valid.
     */
    abstract boolean validateFrameRead(ChannelHandlerContext ctx, Http2Frame frame);

    /**
     * Validates whether the passed {@link Http2Frame} is valid when written. If the frame is invalid, this method
     * should take appropriate action to channel and {@link Http2Frame} dispose the frame. Only valid frames will be
     * written to the channel.
     *
     * @param ctx {@link ChannelHandlerContext} for this handler.
     * @param frame to validate.
     */
    abstract void validateFrameWritten(ChannelHandlerContext ctx, Http2Frame frame);

    protected static void connectionCloseOnUnexpectedFrame(Channel http2Channel, Http2Frame unexpectedFrame) {
        logger.debug("Invalid frame {} received on stream id: {} for HTTP/2 channel {}, initiating channel closure.",
                unexpectedFrame.toString(), unexpectedFrame.streamId(), http2Channel);
        ReferenceCountUtil.release(unexpectedFrame);
        http2Channel.pipeline().fireUserEventTriggered(PROTOCOL_ERROR_EVENT_NO_DEBUG_DATA);
    }
}
