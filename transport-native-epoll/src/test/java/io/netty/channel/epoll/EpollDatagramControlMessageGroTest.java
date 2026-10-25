/*
 * Copyright 2026 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Receives the datagrams with {@code UDP_GRO} enabled, which means that datagrams which are sent via
 * {@code UDP_SEGMENT} are coalesced by the kernel and need to be split by us again.
 */
public class EpollDatagramControlMessageGroTest extends EpollDatagramControlMessageTest {

    @BeforeAll
    public static void assumeGroSupported() {
        assumeTrue(EpollDatagramChannel.isSegmentedDatagramPacketSupported());
    }

    @Override
    protected void configureReceiver(Bootstrap bootstrap) {
        bootstrap.option(EpollChannelOption.UDP_GRO, true);
    }
}
