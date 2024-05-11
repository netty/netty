/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.internal.PlatformDependent;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class DatagramMulticastIPv6Test extends DatagramMulticastTest {

    @Override
    public void testMulticast(Bootstrap sb, Bootstrap cb) throws Throwable {
        // Not works on windows atm.
        // See https://github.com/netty/netty/issues/11285
        assumeFalse(PlatformDependent.isWindows());
        super.testMulticast(sb, cb);
    }

    @Override
    protected SocketProtocolFamily socketProtocolFamily() {
        return SocketProtocolFamily.INET6;
    }
}
