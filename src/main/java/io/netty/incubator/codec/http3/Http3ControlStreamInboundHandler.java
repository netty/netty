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
import io.netty.util.ReferenceCountUtil;

final class Http3ControlStreamInboundHandler extends Http3FrameTypeValidationHandler<Http3ControlStreamFrame> {
    final boolean server;
    private final ChannelHandler controlFrameHandler;
    private boolean firstFrameRead;
    private Long receivedGoawayId;
    private Long receivedMaxPushId;

    Http3ControlStreamInboundHandler(boolean server, ChannelHandler controlFrameHandler) {
        super(Http3ControlStreamFrame.class);
        this.server = server;
        this.controlFrameHandler = controlFrameHandler;
    }

    boolean isServer() {
        return server;
    }

    boolean isGoAwayReceived() {
        return receivedGoawayId != null;
    }

    private boolean forwardControlFrames() {
        return controlFrameHandler != null;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        // The user want's to be notified about control frames, add the handler to the pipeline.
        if (controlFrameHandler != null) {
            ctx.pipeline().addLast(controlFrameHandler);
        }
    }

    @Override
    void frameTypeUnexpected(ChannelHandlerContext ctx, Object frame) {
        if (!firstFrameRead && !(frame instanceof Http3SettingsFrame)) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_MISSING_SETTINGS,
                    "Missing settings frame.", forwardControlFrames());
            ReferenceCountUtil.release(frame);
            return;
        }
        super.frameTypeUnexpected(ctx, frame);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Http3ControlStreamFrame frame) {
        boolean isSettingsFrame = frame instanceof Http3SettingsFrame;
        if (!firstFrameRead && !isSettingsFrame) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_MISSING_SETTINGS,
                    "Missing settings frame.", forwardControlFrames());
            ReferenceCountUtil.release(frame);
            return;
        }
        if (firstFrameRead && isSettingsFrame) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                    "Second settings frame received.", forwardControlFrames());
            ReferenceCountUtil.release(frame);
            return;
        }
        firstFrameRead = true;

        final boolean valid;
        if (isSettingsFrame) {
            valid = handleHttp3SettingsFrame(ctx, (Http3SettingsFrame) frame);
        } else if (frame instanceof Http3GoAwayFrame) {
            valid = handleHttp3GoAwayFrame(ctx, (Http3GoAwayFrame) frame);
        } else if (frame instanceof Http3MaxPushIdFrame) {
            valid = handleHttp3MaxPushIdFrame(ctx, (Http3MaxPushIdFrame) frame);
        } else if (frame instanceof Http3CancelPushFrame) {
            valid = handleHttp3CancelPushFrame(ctx, (Http3CancelPushFrame) frame);
        } else {
            // We don't need to do any special handling for Http3UnknownFrames as we either pass these to the next#
            // handler or release these directly.
            assert frame instanceof Http3UnknownFrame;
            valid = true;
        }

        if (!valid || controlFrameHandler == null) {
            ReferenceCountUtil.release(frame);
            return;
        }

        // The user did specify ChannelHandler that should be notified about control stream frames.
        // Let's forward the frame so the user can do something with it.
        ctx.fireChannelRead(frame);
    }

    private boolean handleHttp3SettingsFrame(ChannelHandlerContext ctx, Http3SettingsFrame settingsFrame) {
        // TODO: handle this.
        // As we not support dynamic table yet we dont create QPACK encoder / decoder streams.
        // Once we support dynamic table this should be done here as well.
        // TODO: Add QPACK stream creation.
        return true;
    }

    private boolean handleHttp3GoAwayFrame(ChannelHandlerContext ctx, Http3GoAwayFrame goAwayFrame) {
        long id = goAwayFrame.id();
        if (!server && id % 4 != 0) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                    "GOAWAY received with ID of non-request stream.", forwardControlFrames());
            return false;
        }
        if (receivedGoawayId != null && id > receivedGoawayId) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                    "GOAWAY received with ID larger than previously received.", forwardControlFrames());
            return false;
        }
        receivedGoawayId = id;
        return true;
    }

    private boolean handleHttp3MaxPushIdFrame(ChannelHandlerContext ctx, Http3MaxPushIdFrame frame) {
        long id = frame.id();
        if (!server) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                    "MAX_PUSH_ID received by client.", forwardControlFrames());
            return false;
        }
        if (receivedMaxPushId != null && id < receivedMaxPushId) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                    "MAX_PUSH_ID reduced limit.", forwardControlFrames());
            return false;
        }
        receivedMaxPushId = id;
        return true;
    }

    private boolean handleHttp3CancelPushFrame(ChannelHandlerContext ctx, Http3CancelPushFrame cancelPushFrame) {
        // TODO: handle this.
        return true;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();

        // control streams should always be processed, no matter what the user is doing in terms of
        // configuration and AUTO_READ.
        Http3CodecUtils.readIfNoAutoRead(ctx);
    }

    @Override
    public boolean isSharable() {
        // Not sharable as it keeps state.
        return false;
    }
}
