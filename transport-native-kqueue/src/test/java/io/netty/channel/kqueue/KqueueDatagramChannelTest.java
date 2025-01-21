/*
 * Copyright 2023 The Netty Project
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
package io.netty.channel.kqueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class KqueueDatagramChannelTest {

    @BeforeEach
    public void setUp() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testDefaultMaxMessagePerRead() {
        KQueueDatagramChannel channel = new KQueueDatagramChannel();
        assertEquals(16, channel.config().getMaxMessagesPerRead());
        channel.unsafe().closeForcibly();
    }
}
