/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jboss.netty.channel;

import java.util.HashSet;
import java.util.Set;


/**
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and producing an aggregated result
 * (success/failure) when all futures have completed.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
public class ChannelFutureAggregator implements ChannelFutureListener {

    private final ChannelFuture aggregateFuture;

    private Set<ChannelFuture> pendingFutures;

    public ChannelFutureAggregator(ChannelFuture aggregateFuture) {
        this.aggregateFuture = aggregateFuture;
    }

    public void addFuture(ChannelFuture future) {
        synchronized(this) {
            if (pendingFutures == null) {
                pendingFutures = new HashSet<ChannelFuture>();
            }
            pendingFutures.add(future);
        }
        future.addListener(this);
    }

    @Override
    public void operationComplete(ChannelFuture future)
            throws Exception {
        if (future.isCancelled()) {
            // TODO: what should the correct behaviour be when a fragment is cancelled?
            // cancel all outstanding fragments and cancel the aggregate?
            return;
        }

        synchronized (this) {
            if (pendingFutures == null) {
                aggregateFuture.setSuccess();
            } else {
                pendingFutures.remove(future);
                if (!future.isSuccess()) {
                    aggregateFuture.setFailure(future.getCause());
                    for (ChannelFuture pendingFuture: pendingFutures) {
                        pendingFuture.cancel();
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