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

import io.netty.channel.socket.SocketProtocolFamily;
import org.junit.jupiter.api.BeforeAll;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.Channel;
import java.nio.channels.spi.SelectorProvider;

public class DatagramUnicastIPv6Test extends DatagramUnicastInetTest {

    @BeforeAll
    public static void assumeIpv6Supported() {
        try {
            Channel channel = SelectorProvider.provider().openDatagramChannel(StandardProtocolFamily.INET6);
            channel.close();
        } catch (UnsupportedOperationException e) {
           throw new TestAbortedException("IPv6 not supported", e);
        } catch (IOException ignore) {
            // Ignore
        }
    }
    @Override
    protected SocketProtocolFamily socketProtocolFamily() {
        return SocketProtocolFamily.INET6;
    }
}
