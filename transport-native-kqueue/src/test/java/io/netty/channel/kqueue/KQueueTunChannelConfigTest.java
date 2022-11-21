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
package io.netty.channel.kqueue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.netty.channel.socket.TunChannelOption.TUN_MTU;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KQueueTunChannelConfigTest {
    @BeforeAll
    public static void loadJNI() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testSetGetMtu() throws IOException {
        KQueueTunChannel channel = null;
        try {
            channel = new KQueueTunChannel();
            final KQueueChannelConfig config = channel.config();

            assertEquals(0, config.getOption(TUN_MTU));

            config.setOption(TUN_MTU, 1500);
            assertEquals(1500, config.getOption(TUN_MTU));
        } finally {
            if (channel != null) {
                channel.socket.close();
            }
        }
    }
}
