/*
 * Copyright 2012 The Netty Project
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
import io.netty5.util.concurrent.OrderedEventExecutor;

/**
 * Will handle all the I/O operations for a {@link Channel} once registered.
 *<p>
 * One {@link EventLoop} instance will usually handle more than one {@link Channel} but this may depend on
 * implementation details and internals.
 */
public interface EventLoop extends OrderedEventExecutor, EventLoopGroup {

    @Override
    default EventLoop next() {
        return this;
    }

    /**
     * Register the {@link Channel} to the {@link EventLoop} for I/O processing.
     *
     * @param channel   the {@link Channel} to register.
     * @return          the {@link Future} that is notified once the operations completes.
     */
    Future<Void> registerForIo(Channel channel);

    /**
     * Deregister the {@link Channel} from the {@link EventLoop} for I/O processing.
     *
     * @param channel   the {@link Channel} to deregister.
     * @return          the {@link Future} that is notified once the operations completes.
     */
    Future<Void> deregisterForIo(Channel channel);
}
