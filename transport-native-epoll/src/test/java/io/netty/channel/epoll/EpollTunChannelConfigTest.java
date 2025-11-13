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
package io.netty.channel.epoll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.netty.channel.epoll.EpollTunChannelOption.IFF_MULTI_QUEUE;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpollTunChannelConfigTest {
    @BeforeAll
    public static void loadJNI() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testSetGetMtu() throws IOException {
        EpollTunChannel channel = null;
        try {
            channel = new EpollTunChannel();
            final EpollChannelConfig config = channel.config();

            assertEquals(0, config.getOption(TUN_MTU));

            config.setOption(TUN_MTU, 1500);
            assertEquals(1500, config.getOption(TUN_MTU));
        } finally {
            if (channel != null) {
                channel.socket.close();
            }
        }
    }

    @Test
    public void testSetGetMultiQueue() throws IOException {
        EpollTunChannel channel = null;
        try {
            channel = new EpollTunChannel();
            final EpollChannelConfig config = channel.config();

            assertFalse(config.getOption(IFF_MULTI_QUEUE));

            config.setOption(IFF_MULTI_QUEUE, true);
            assertTrue(config.getOption(IFF_MULTI_QUEUE));
        } finally {
            if (channel != null) {
                channel.socket.close();
            }
        }
    }
}
