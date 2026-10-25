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
package io.netty.channel.uring;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.tests.DatagramControlMessageTest;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringDatagramControlMessageTest extends DatagramControlMessageTest {

    @BeforeAll
    public static void loadNative() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected EventLoopGroup newGroup() {
        return new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
    }

    @Override
    protected DatagramChannel newChannel(SocketProtocolFamily family) {
        return new IoUringDatagramChannel(family);
    }

    @Override
    protected boolean isUdpSegmentSupported() {
        return IoUringDatagramChannel.isSegmentedDatagramPacketSupported();
    }
}
