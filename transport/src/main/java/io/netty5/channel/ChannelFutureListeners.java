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
package io.netty5.channel;

import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureContextListener;

/**
 * {@link FutureContextListener} listeners that take a context, and listens to the result of a {@link Future}.
 * The result of the asynchronous {@link ChannelOutboundInvoker} I/O operation is notified once this listener is added
 * by calling {@link Future#addListener(Object, FutureContextListener)} with the {@link ChannelOutboundInvoker} /
 * {@link Channel} as context.
 */
public final class ChannelFutureListeners {

    /**
     * A {@link FutureContextListener} that closes the {@link ChannelOutboundInvoker} which is associated with
     * the specified {@link Future}.
     */
    public static final FutureContextListener<ChannelOutboundInvoker, Object> CLOSE = (c, f) -> c.close();

    /**
     * A {@link FutureContextListener} that closes the {@link ChannelOutboundInvoker} when the operation ended up with
     * a failure or cancellation rather than a success.
     */
    public static final FutureContextListener<ChannelOutboundInvoker, Object> CLOSE_ON_FAILURE = (c, f) -> {
        if (f.isFailed()) {
            c.close();
        }
    };

    /**
     * A {@link FutureContextListener} that forwards the {@link Throwable} of the {@link Future} into the {@link
     * ChannelPipeline}. This mimics the old behavior of Netty 3.
     */
    public static final FutureContextListener<Channel, Object> FIRE_EXCEPTION_ON_FAILURE = (c, f) -> {
        if (f.isFailed()) {
            c.pipeline().fireExceptionCaught(f.cause());
        }
    };

    private ChannelFutureListeners() { }
}
