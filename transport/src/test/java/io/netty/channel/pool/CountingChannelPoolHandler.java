/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicInteger;

final class CountingChannelPoolHandler implements ChannelPoolHandler {
    private final AtomicInteger channelCount = new AtomicInteger(0);
    private final AtomicInteger acquiredCount = new AtomicInteger(0);
    private final AtomicInteger releasedCount = new AtomicInteger(0);

    @Override
    public void channelCreated(Channel ch) {
        channelCount.incrementAndGet();
    }

    @Override
    public void channelReleased(Channel ch) {
        releasedCount.incrementAndGet();
    }

    @Override
    public void channelAcquired(Channel ch) {
        acquiredCount.incrementAndGet();
    }

    public int channelCount() {
        return channelCount.get();
    }

    public int acquiredCount() {
        return acquiredCount.get();
    }

    public int releasedCount() {
        return releasedCount.get();
    }
}
