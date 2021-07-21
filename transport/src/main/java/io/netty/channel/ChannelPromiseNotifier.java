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
package io.netty.channel;

import io.netty.util.concurrent.PromiseNotifier;

/**
 * ChannelFutureListener implementation which takes other {@link ChannelPromise}(s) and notifies them on completion.
 *
 * @deprecated use {@link PromiseNotifier}.
 */
@Deprecated
public final class ChannelPromiseNotifier
    extends PromiseNotifier<Void, ChannelFuture>
    implements ChannelFutureListener {

    /**
     * Create a new instance
     *
     * @param promises  the {@link ChannelPromise}s to notify once this {@link ChannelFutureListener} is notified.
     */
    public ChannelPromiseNotifier(ChannelPromise... promises) {
        super(promises);
    }

    /**
     * Create a new instance
     *
     * @param logNotifyFailure {@code true} if logging should be done in case notification fails.
     * @param promises  the {@link ChannelPromise}s to notify once this {@link ChannelFutureListener} is notified.
     */
    public ChannelPromiseNotifier(boolean logNotifyFailure, ChannelPromise... promises) {
        super(logNotifyFailure, promises);
    }
}
