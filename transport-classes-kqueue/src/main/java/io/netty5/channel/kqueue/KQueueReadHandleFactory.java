/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.channel.MaxMessagesReadHandleFactory;

/**
 * {@link MaxMessagesReadHandleFactory} which always try to allocate buffers as big as kqueue tells us
 * in terms of pending bytes to read.
 */
public final class KQueueReadHandleFactory extends MaxMessagesReadHandleFactory {

    public KQueueReadHandleFactory() { }

    public KQueueReadHandleFactory(int maxMessagesPerRead) {
        super(maxMessagesPerRead);
    }

    @Override
    public MaxMessageReadHandle newMaxMessageHandle(int maxMessagesPerRead) {
        return new KQueueReadHandle(maxMessagesPerRead);
    }

    static final class KQueueReadHandle extends MaxMessageReadHandle {
        private int capacity = 512;

        KQueueReadHandle(int maxMessagesPerRead) {
            super(maxMessagesPerRead);
        }

        void bufferCapacity(int capacity) {
            this.capacity = capacity == 0 ? 512 : capacity;
        }

        @Override
        public int prepareRead() {
            // bufferCapacity(...) is called with what KQueue tells us.
            return capacity * super.prepareRead();
        }
    }
}
