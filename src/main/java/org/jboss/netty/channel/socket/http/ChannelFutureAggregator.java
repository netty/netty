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

package org.jboss.netty.channel.socket.http;

import java.util.HashSet;
import java.util.Set;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and producing an aggregated result
 * (success/failure) when all futures have completed.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class ChannelFutureAggregator implements ChannelFutureListener {

    private final ChannelFuture aggregateFuture;

    private final Set<ChannelFuture> pendingFutures;

    public ChannelFutureAggregator(ChannelFuture aggregateFuture) {
        this.aggregateFuture = aggregateFuture;
        pendingFutures = new HashSet<ChannelFuture>();
    }

    public void addFuture(ChannelFuture future) {
        pendingFutures.add(future);
        future.addListener(this);
    }

    public synchronized void operationComplete(ChannelFuture future)
            throws Exception {
        if (future.isCancelled()) {
            // TODO: what should the correct behaviour be when a fragment is cancelled?
            // cancel all outstanding fragments and cancel the aggregate?
            return;
        }

        pendingFutures.remove(future);
        if (!future.isSuccess()) {
            aggregateFuture.setFailure(future.getCause());
            for (ChannelFuture pendingFuture: pendingFutures) {
                pendingFuture.cancel();
            }
            return;
        }

        if (pendingFutures.isEmpty()) {
            aggregateFuture.setSuccess();
        }
    }
}