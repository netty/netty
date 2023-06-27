/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultMaxMessagesRecvByteBufAllocatorTest {

    private DefaultMaxMessagesRecvByteBufAllocator newAllocator(boolean ignoreReadBytes) {
        return new DefaultMaxMessagesRecvByteBufAllocator(2, ignoreReadBytes) {
            @Override
            public Handle newHandle() {
                return new MaxMessageHandle() {
                    @Override
                    public int guess() {
                        return 0;
                    }
                };
            }
        };
    }

    @Test
    public void testRespectReadBytes() {
        DefaultMaxMessagesRecvByteBufAllocator allocator = newAllocator(false);
        RecvByteBufAllocator.Handle handle = allocator.newHandle();

        EmbeddedChannel channel = new EmbeddedChannel();
        handle.reset(channel.config());
        handle.incMessagesRead(1);
        assertFalse(handle.continueReading());

        handle.reset(channel.config());
        handle.incMessagesRead(1);
        handle.attemptedBytesRead(1);
        handle.lastBytesRead(1);
        assertTrue(handle.continueReading());
        channel.finish();
    }

    @Test
    public void testIgnoreReadBytes() {
        DefaultMaxMessagesRecvByteBufAllocator allocator = newAllocator(true);
        RecvByteBufAllocator.Handle handle = allocator.newHandle();

        EmbeddedChannel channel = new EmbeddedChannel();
        handle.reset(channel.config());
        handle.incMessagesRead(1);
        assertTrue(handle.continueReading());
        handle.incMessagesRead(1);
        assertFalse(handle.continueReading());

        handle.reset(channel.config());
        handle.attemptedBytesRead(0);
        handle.lastBytesRead(0);
        assertTrue(handle.continueReading());
        channel.finish();
    }
}
