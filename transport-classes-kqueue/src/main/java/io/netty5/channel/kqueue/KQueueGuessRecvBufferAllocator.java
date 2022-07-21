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

import io.netty5.channel.DefaultMaxMessagesRecvBufferAllocator;

/**
 * {@link DefaultMaxMessagesRecvBufferAllocator} which always try to allocate buffers as big as kqueue tells us
 * in terms of pending bytes to read.
 */
public final class KQueueGuessRecvBufferAllocator extends DefaultMaxMessagesRecvBufferAllocator {
    @Override
    public Handle newHandle() {
        return new MaxMessageHandle() {

            @Override
            public int guess() {
                // attemptedBytesRead(...) is called with what KQueue tells us.
                int guess = attemptedBytesRead();
                if (guess == 0) {
                    // Just in case attemptedBytesRead() returns 0 let us allocate something small.
                    return 512;
                }
                return guess;
            }
        };
    }
}
