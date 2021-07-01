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

import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * A {@link ChannelHandlerAdapter} that feeds flow control events to the associated
 * {@link DefaultChannelFlowControlledBytesDistributor}. <p>
 */
final class RawFlowControlFrameInspector extends ChannelHandlerAdapter {
    private final RawHttp2RequestStreamCodecState codecStates;
    private final DefaultChannelFlowControlledBytesDistributor distributor;

    RawFlowControlFrameInspector(RawHttp2RequestStreamCodecState codecStates,
                                 DefaultChannelFlowControlledBytesDistributor distributor) {
        this.codecStates = checkNotNullWithIAE(codecStates, "codecStates");
        this.distributor = checkNotNullWithIAE(distributor, "distributor");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2RequestStreamFrame)) {
            ctx.fireChannelRead(msg);
            return;
        }
        Http2RequestStreamFrame frame = (Http2RequestStreamFrame) msg;
        final Http2RequestStreamCodecState remoteState = codecStates.remoteStateForStream(frame.streamId());
        ctx.fireChannelRead(msg);
        if (msg instanceof Http2DataFrame) {
            distributor.bytesRead(frame.streamId(), ((Http2DataFrame) msg).initialFlowControlledBytes());
        }
        if (remoteState.terminated()) {
            distributor.streamInputClosed(frame.streamId());
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Http2RequestStreamFrame)) {
            return ctx.write(msg);
        }

        Http2RequestStreamFrame frame = (Http2RequestStreamFrame) msg;
        final Http2RequestStreamCodecState state = codecStates.localStateForStream(frame.streamId());
        final Future<Void> future = ctx.write(msg);
        final boolean sendOutputClosed = state.terminated();
        if (frame instanceof Http2DataFrame || sendOutputClosed) {
            if (future.isDone()) {
                invokeDistributorOnWriteDone(future, sendOutputClosed, frame);
            } else {
                future.addListener(f -> invokeDistributorOnWriteDone(f, sendOutputClosed, frame));
            }
        }
        return future;
    }

    private void invokeDistributorOnWriteDone(Future<?> future, boolean sendOutputClosed,
                                              Http2RequestStreamFrame frame) {
        if (future.isSuccess()) {
            if (frame instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) frame;
                if (dataFrame.initialFlowControlledBytes() > 0) {
                    distributor.bytesWritten(frame.streamId(), dataFrame.initialFlowControlledBytes());
                }
            }
            if (sendOutputClosed) {
                distributor.streamOutputClosed(frame.streamId());
            }
        }
    }
}
