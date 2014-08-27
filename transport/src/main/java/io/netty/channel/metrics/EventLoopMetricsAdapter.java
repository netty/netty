/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.metrics;

import io.netty.channel.EventLoop;

/**
 * Skeleton implementation of {@link EventLoopMetrics}.
 */
public class EventLoopMetricsAdapter implements EventLoopMetrics {

    @Override
    public void channelActive() {
        // NOOP
    }

    @Override
    public void channelInactive() {
        // NOOP
    }

    @Override
    public void channelRegistered() {
        // NOOP
    }

    @Override
    public void channelUnregistered() {
        // NOOP
    }

    @Override
    public void readBytes(long bytes) {
        // NOOP
    }

    @Override
    public void writeBytes(long bytes) {
        // NOOP
    }

    @Override
    public void startProcessIo() {
        // NOOP
    }

    @Override
    public void stopProcessIo() {
        // NOOP
    }

    @Override
    public void startExecuteTasks() {
        // NOOP
    }

    @Override
    public void stopExecuteTasks() {
        // NOOP
    }

    @Override
    public void init(EventLoop eventLoop) {
        // NOOP
    }
}
