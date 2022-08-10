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
package io.netty5.channel;

import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaxMessagesReadHandleFactoryTest {

    private MaxMessagesReadHandleFactory newAllocator() {
        return new MaxMessagesReadHandleFactory(2) {
            @Override
            public MaxMessageReadHandle newMaxMessageHandle(int maxMessagesPerRead) {
                return new MaxMessageReadHandle(maxMessagesPerRead) {
                    @Override
                    public int estimatedBufferCapacity() {
                        return 0;
                    }
                };
            }
        };
    }

    @Test
    public void testRespectMaxMessages() {
        MaxMessagesReadHandleFactory allocator = newAllocator();
        ReadHandleFactory.ReadHandle handle = allocator.newHandle();

        EmbeddedChannel channel = new EmbeddedChannel();
        assertTrue(handle.lastRead(0, 0, 1));
        assertFalse(handle.lastRead(0, 0, 1));

        handle.readComplete();
        assertTrue(handle.lastRead(1, 1, 1));
        channel.finish();
    }

    @Test
    public void testIgnoreReadBytes() {
        MaxMessagesReadHandleFactory allocator = newAllocator();
        ReadHandleFactory.ReadHandle handle = allocator.newHandle();

        EmbeddedChannel channel = new EmbeddedChannel();
        assertTrue(handle.lastRead(0, 0, 1));
        assertFalse(handle.lastRead(0, 0, 1));

        handle.readComplete();
        assertTrue(handle.lastRead(0, 0, 0));
        channel.finish();
    }
}
