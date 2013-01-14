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
public final class ChannelPromiseAggregator implements ChannelFutureListener {

    private final ChannelPromise aggregatePromise;

    private Set<ChannelPromise> pendingPromises;

    public ChannelPromiseAggregator(ChannelPromise aggregatePromise) {
        this.aggregatePromise = aggregatePromise;
    }

    public void addFuture(ChannelPromise promise) {
        synchronized (this) {
            if (pendingPromises == null) {
                pendingPromises = new HashSet<ChannelPromise>();
            }
            pendingPromises.add(promise);
        }
        promise.addListener(this);
    }

    @Override
    public synchronized void operationComplete(ChannelFuture future) throws Exception {
        if (pendingPromises == null) {
            aggregatePromise.setSuccess();
        } else {
            pendingPromises.remove(future);
            if (!future.isSuccess()) {
                aggregatePromise.setFailure(future.cause());
                for (ChannelPromise pendingFuture : pendingPromises) {
                    pendingFuture.setFailure(future.cause());
                }
            } else {
                if (pendingPromises.isEmpty()) {
                    aggregatePromise.setSuccess();
                }
            }
        }
    }
}
