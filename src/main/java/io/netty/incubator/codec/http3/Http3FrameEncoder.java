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
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

public final class Http3FrameEncoder extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Http3DataFrame) {
            writeDataFrame(ctx, (Http3DataFrame) msg, promise);
        } else if (msg instanceof Http3HeadersFrame) {
            writeHeadersFrame(ctx, (Http3HeadersFrame) msg, promise);
        } else if (msg instanceof Http3CancelPushFrame) {
            writeCancelPushFrame(ctx, (Http3CancelPushFrame) msg, promise);
        } else if (msg instanceof Http3SettingsFrame) {
            writeSettingsFrame(ctx, (Http3SettingsFrame) msg, promise);
        } else if (msg instanceof Http3PushPromiseFrame) {
            writePushPromiseFrame(ctx, (Http3PushPromiseFrame) msg, promise);
        } else if (msg instanceof Http3GoAwayFrame) {
            writeGoAwayFrame(ctx, (Http3GoAwayFrame) msg, promise);
        } else if (msg instanceof Http3MaxPushIdFrame) {
            writeMaxPushIdFrame(ctx, (Http3MaxPushIdFrame) msg, promise);
        } else {
            unsupported(msg, promise);
        }
    }

    private static void writeDataFrame(
            ChannelHandlerContext ctx, Http3DataFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writeHeadersFrame(
            ChannelHandlerContext ctx, Http3HeadersFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writeCancelPushFrame(
            ChannelHandlerContext ctx, Http3CancelPushFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writeSettingsFrame(
            ChannelHandlerContext ctx, Http3SettingsFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writePushPromiseFrame(
            ChannelHandlerContext ctx, Http3PushPromiseFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writeGoAwayFrame(
            ChannelHandlerContext ctx, Http3GoAwayFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }

    private static void writeMaxPushIdFrame(
            ChannelHandlerContext ctx, Http3MaxPushIdFrame frame, ChannelPromise promise) {
        // TODO: Implement me
        unsupported(frame, promise);
    }
    private static void unsupported(Object msg, ChannelPromise promise) {
        ReferenceCountUtil.release(msg);
        promise.setFailure(new UnsupportedOperationException());
    }
}
