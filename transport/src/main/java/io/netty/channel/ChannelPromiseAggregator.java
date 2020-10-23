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

import io.netty.util.concurrent.PromiseAggregator;
import io.netty.util.concurrent.PromiseCombiner;

/**
 * @deprecated Use {@link PromiseCombiner}
 *
 * Class which is used to consolidate multiple channel futures into one, by
 * listening to the individual futures and producing an aggregated result
 * (success/failure) when all futures have completed.
 */
@Deprecated
public final class ChannelPromiseAggregator
    extends PromiseAggregator<Void, ChannelFuture>
    implements ChannelFutureListener {

    public ChannelPromiseAggregator(ChannelPromise aggregatePromise) {
        super(aggregatePromise);
    }

}
