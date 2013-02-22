/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.flowcontrol;

import io.netty.buffer.Buf;
import io.netty.buffer.BufType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOperationHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * {@link ChannelOperationHandlerAdapter} which will fire an {@link FlowControlStateEvent} once the outbound
 * buffer of a {@link Channel} change state. You can then use this event to get notified once the {@link Channel}
 * is saturated and once it is safe to try a write / flush operation again.
 */
public class FlowControlHandler extends ChannelOperationHandlerAdapter {

    private final FlowControlStateEvent state = new FlowControlStateEvent();
    private final ChannelFutureListener listener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!state.isWritable() && isWritable()) {
                state.updateWritable(true);
                ctx.fireUserEventTriggered(state);
            }
        }
    };

    private final int minWritable;
    private ChannelHandlerContext ctx;
    private Buf outboundBuf;

    /**
     * Create a new instance
     *
     * @param minWritable   the minimum elements that must fit in the outbound buffer before it is considered to be
     *                      writable.
     */
    public FlowControlHandler(int minWritable) {
        if (minWritable < 1) {
            throw new IllegalArgumentException("minWritable must be >= 1");
        }
        this.minWritable = minWritable;
    }

    @Override
    public void flush(final ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (state.isWritable()) {
            if (!isWritable()) {
                state.updateWritable(false);
                ctx.fireUserEventTriggered(state);
            }
        }
        if (!state.isWritable()) {
            // add listener if it is not writable atm
            promise.addListener(listener);
        }
        ctx.flush(promise);
    }

    private boolean isWritable() {
        return outboundBuf.isWritable(minWritable);
    }

    @Override
    public void beforeAdd(final ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        Channel channel = ctx.channel();
        if (channel.metadata().bufferType() == BufType.BYTE) {
            outboundBuf = channel.unsafe().directOutboundContext().outboundByteBuffer();
        } else {
            outboundBuf = channel.unsafe().directOutboundContext().outboundMessageBuffer();
        }
        super.beforeAdd(ctx);
    }
}
