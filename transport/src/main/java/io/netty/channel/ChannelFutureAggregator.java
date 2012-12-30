/*
 * Copyright 2012 The Netty Project
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

package io.netty.channel;

import java.util.HashSet;
import java.util.Set;


/**
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and producing an aggregated result
 * (success/failure) when all futures have completed.
 */
public final class ChannelFutureAggregator implements ChannelFutureListener {

    private final ChannelPromise aggregateFuture;

    private Set<ChannelPromise> pendingFutures;

    public ChannelFutureAggregator(ChannelPromise aggregateFuture) {
        this.aggregateFuture = aggregateFuture;
    }

    public void addFuture(ChannelPromise future) {
        synchronized (this) {
            if (pendingFutures == null) {
                pendingFutures = new HashSet<ChannelPromise>();
            }
            pendingFutures.add(future);
        }
        future.addListener(this);
    }

    @Override
    public void operationComplete(ChannelFuture future)
            throws Exception {

        synchronized (this) {
            if (pendingFutures == null) {
                aggregateFuture.setSuccess();
            } else {
                pendingFutures.remove(future);
                if (!future.isSuccess()) {
                    aggregateFuture.setFailure(future.cause());
                    for (ChannelPromise pendingFuture: pendingFutures) {
                        pendingFuture.setFailure(future.cause());
                    }
                } else {
                    if (pendingFutures.isEmpty()) {
                        aggregateFuture.setSuccess();
                    }
                }
            }
        }
    }
}
