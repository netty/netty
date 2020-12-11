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
import io.netty.util.ReferenceCountUtil;

final class Http3ControlStreamInboundHandler extends Http3FrameTypeValidationHandler<Http3ControlStreamFrame> {
    private final boolean server;
    private final boolean forwardControlFrames;
    private boolean firstFrameRead;
    private Long receivedGoawayId;
    private Long receivedMaxPushId;

    Http3ControlStreamInboundHandler(boolean server, boolean forwardControlFrames) {
        super(Http3ControlStreamFrame.class);
        this.server = server;
        this.forwardControlFrames = forwardControlFrames;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Http3ControlStreamFrame frame) {
        final boolean firstFrame;
        if (!firstFrameRead) {
            firstFrameRead = true;
            firstFrame = true;
        } else {
            firstFrame = false;
        }
        if (firstFrame && !(frame instanceof Http3SettingsFrame)) {
            Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_MISSING_SETTINGS,
                    "Missing settings frame.", forwardControlFrames);
            ReferenceCountUtil.release(frame);
            return;
        } else if (frame instanceof Http3SettingsFrame) {
            // TODO: handle this.
            Http3SettingsFrame settingsFrame = (Http3SettingsFrame) frame;
            // As we not support dynamic table yet we dont create QPACK encoder / decoder streams.
            // Once we support dynamic table this should be done here as well.
            // TODO: Add QPACK stream creation.
        } else if (frame instanceof Http3GoAwayFrame) {
            Http3GoAwayFrame goAwayFrame = (Http3GoAwayFrame) frame;
            long id = goAwayFrame.id();
            if (!server && id % 4 != 0) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "GOAWAY received with ID of non-request stream.", forwardControlFrames);
                ReferenceCountUtil.release(frame);
                return;
            } else if (receivedGoawayId != null && id > receivedGoawayId) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                        "GOAWAY received with ID larger than previously received.", forwardControlFrames);
                ReferenceCountUtil.release(frame);
                return;
            } else {
                receivedGoawayId = id;
            }
        } else if (frame instanceof Http3MaxPushIdFrame) {
            long id = ((Http3MaxPushIdFrame) frame).id();

            if (!server) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_FRAME_UNEXPECTED,
                        "MAX_PUSH_ID received by client.", forwardControlFrames);
                ReferenceCountUtil.release(frame);
                return;
            } else if (receivedMaxPushId != null && id < receivedMaxPushId) {
                Http3CodecUtils.connectionError(ctx, Http3ErrorCode.H3_ID_ERROR,
                        "MAX_PUSH_ID reduced limit.", forwardControlFrames);
                ReferenceCountUtil.release(frame);
                return;
            } else {
                receivedMaxPushId = id;
            }
        } else if (frame instanceof Http3CancelPushFrame) {
            // TODO: implement me
        }

        // We don't need to do any special handling for Http3UnknownFrames as we either pass these to the next handler
        // or release these directly.
        if (forwardControlFrames) {
            // The user did specify ChannelHandler that should be notified about control stream frames.
            // Let's forward the frame so the user can do something with it.
            ctx.fireChannelRead(frame);
        } else {
            // We handled the frame, release it.
            ReferenceCountUtil.release(frame);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();

        // control streams should always be processed, no matter what the user is doing in terms of
        // configuration and AUTO_READ.
        Http3CodecUtils.readIfNoAutoRead(ctx);
    }
}
