/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.nio;

import io.netty.channel.SelectStrategy;
import io.netty.util.IntSupplier;

/**
 * Default {@link SelectStrategy} which triggers the blocking select without backoff if no
 * tasks are in the queue to be processed.
 */
final class DefaultSelectStrategy implements SelectStrategy {

    public static final SelectStrategy INSTANCE = new DefaultSelectStrategy();

    private DefaultSelectStrategy() {
        // singleton.
    }

    @Override
    public int calculateStrategy(final IntSupplier supplier, final boolean hasTasks)
        throws Exception {
        return hasTasks ? supplier.get() : SelectStrategy.SELECT;
    }
}
