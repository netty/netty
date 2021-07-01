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
package io.netty.handler.codec.h2new;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

public class Http2RawChannelFramesValidator extends AbstractHttp2StreamFramesValidator
        implements RawHttp2RequestStreamCodecState {
    private final IntObjectMap<DefaultHttp2RequestStreamCodecState> remote = new IntObjectHashMap<>();
    private final IntObjectMap<DefaultHttp2RequestStreamCodecState> local = new IntObjectHashMap<>();

    @Override
    boolean validateFrameRead(ChannelHandlerContext ctx, Http2Frame frame) {
        if (frame.streamId() == 0) {
            if (!(frame instanceof Http2ControlStreamFrame)) {
                connectionCloseOnUnexpectedFrame(ctx.channel().parent(), frame);
                return false;
            }
            // TODO: Verify control stream frame ordering (settings received)
            return true;
        }

        if (!(frame instanceof Http2RequestStreamFrame)) {
            connectionCloseOnUnexpectedFrame(ctx.channel().parent(), frame);
            return false;
        }
        Http2RequestStreamFrame requestStreamFrame = (Http2RequestStreamFrame) frame;
        final DefaultHttp2RequestStreamCodecState state = remoteStateForStream(frame.streamId());
        if (!state.evaluateFrame(requestStreamFrame)) {
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
            localStateForStream(requestStreamFrame.streamId()).evaluateFrame(requestStreamFrame);
        }
    }

    @Override
    public DefaultHttp2RequestStreamCodecState localStateForStream(int streamId) {
        return local.computeIfAbsent(streamId, __ -> new DefaultHttp2RequestStreamCodecState());
    }

    @Override
    public DefaultHttp2RequestStreamCodecState remoteStateForStream(int streamId) {
        return remote.computeIfAbsent(streamId, __ -> new DefaultHttp2RequestStreamCodecState());
    }
}
