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

import io.netty.testsuite.transport.socket.TunChannelTest;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;

public class EpollTunChannelTest extends TunChannelTest {
    @BeforeAll
    public static void loadJNI() {
        Epoll.ensureAvailability();
    }

    public EpollTunChannelTest() {
        super(new EpollEventLoopGroup(1), EpollTunChannel.class, EpollDatagramChannel.class);
    }

    @Override
    protected void attachAddressAndNetmask(String name,
                                           String address,
                                           int netmask) throws IOException {
        exec("/sbin/ip", "-4", "addr", "add", address + '/' + netmask, "dev", name);
        exec("/sbin/ip", "link", "set", "dev", name, "up");
    }
}
