/*
 * Copyright 2021 The Netty Project
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
package io.netty.channel;

import java.util.Objects;

final class VoidChannelOutboundInvokerCallback implements ChannelOutboundInvokerCallback, ChannelFutureListener {

    private static final VoidChannelOutboundInvokerCallback NOOP = new VoidChannelOutboundInvokerCallback(null);
    private final ChannelInboundInvoker inboundInvoker;

    static VoidChannelOutboundInvokerCallback noop() {
        return VoidChannelOutboundInvokerCallback.NOOP;
    }

    static VoidChannelOutboundInvokerCallback newInstance(ChannelInboundInvoker inboundInvoker) {
        return new VoidChannelOutboundInvokerCallback(
                Objects.requireNonNull(inboundInvoker, "inboundInvoker"));
    }

    private VoidChannelOutboundInvokerCallback(ChannelInboundInvoker inboundInvoker) {
        this.inboundInvoker = inboundInvoker;
    }

    @Override
    public void onSuccess() {
        // NOOP.
    }

    @Override
    public void onError(Throwable cause) {
        if (inboundInvoker != null) {
            inboundInvoker.fireExceptionCaught(cause);
        }
    }

    @Override
    public void notifyWhenFutureCompletes(ChannelFuture future) {
        if (inboundInvoker != null) {
            future.addListener(this);
        }
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
            onError(future.cause());
        }
    }
}
