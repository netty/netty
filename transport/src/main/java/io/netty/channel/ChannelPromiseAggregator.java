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

import java.util.LinkedHashSet;
import java.util.Set;


/**
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and producing an aggregated result
 * (success/failure) when all futures have completed.
 */
public final class ChannelPromiseAggregator implements ChannelFutureListener {

    private final ChannelPromise aggregatePromise;
    private Set<ChannelPromise> pendingPromises;

    /**
     * Instance an new {@link ChannelPromiseAggregator}
     *
     * @param aggregatePromise  the {@link ChannelPromise} to notify
     */
    public ChannelPromiseAggregator(ChannelPromise aggregatePromise) {
        if (aggregatePromise == null) {
            throw new NullPointerException("aggregatePromise");
        }
        this.aggregatePromise = aggregatePromise;
    }

    /**
     * Add the given {@link ChannelPromise}s to the aggregator.
     */
    public ChannelPromiseAggregator add(ChannelPromise... promises) {
        if (promises == null) {
            throw new NullPointerException("promises");
        }
        if (promises.length == 0) {
            return this;
        }
        synchronized (this) {
            if (pendingPromises == null) {
                int size;
                if (promises.length > 1) {
                    size = promises.length;
                } else {
                    size = 2;
                }
                pendingPromises = new LinkedHashSet<ChannelPromise>(size);
            }
            for (ChannelPromise p: promises) {
                if (p == null) {
                    continue;
                }
                pendingPromises.add(p);
                p.addListener(this);
            }
        }
        return this;
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
