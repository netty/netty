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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureContextListener;

/**
 * {@link FutureContextListener} listeners that take a channel context, and listens to the result of a {@link Future}.
 * The result of the asynchronous {@link Channel} I/O operation is notified once this listener is added by calling
 * {@link Future#addListener(Object, FutureContextListener)} with the {@link Channel} as context.
 */
public enum ChannelFutureListeners implements FutureContextListener<Channel, Object> {
    /**
     * A {@link FutureContextListener} that closes the {@link Channel} which is associated with the specified {@link
     * Future}.
     */
    CLOSE,

    /**
     * A {@link FutureContextListener} that closes the {@link Channel} when the operation ended up with a failure or
     * cancellation rather than a success.
     */
    CLOSE_ON_FAILURE,

    /**
     * A {@link FutureContextListener} that forwards the {@link Throwable} of the {@link Future} into the {@link
     * ChannelPipeline}. This mimics the old behavior of Netty 3.
     */
    FIRE_EXCEPTION_ON_FAILURE;

    @Override
    public void operationComplete(Channel channel, Future<?> future) throws Exception {
        switch (this) {
        case CLOSE:
            channel.close();
            break;
        case CLOSE_ON_FAILURE:
            if (!future.isSuccess()) {
                channel.close();
            }
            break;
        case FIRE_EXCEPTION_ON_FAILURE:
            if (!future.isSuccess()) {
                channel.pipeline().fireExceptionCaught(future.cause());
            }
        }
    }
}
