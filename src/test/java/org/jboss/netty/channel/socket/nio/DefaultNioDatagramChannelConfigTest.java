/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.util.internal.DetectionUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;

import static org.junit.Assert.*;

public class DefaultNioDatagramChannelConfigTest {


    @Test
    public void testMulticastOptions() throws IOException {
        if (DetectionUtil.javaVersion() < 7 || DetectionUtil.isWindows()) {
            // Skip this test on java versions < 7 as its java7 only
            // and on windows as it may fail because of permission problems
            return;
        }

        StandardProtocolFamily family = null;
        NetworkInterface inf = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            inf = interfaces.nextElement();
            Enumeration<InetAddress> addresses = inf.getInetAddresses();
            while(addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) {
                    family = StandardProtocolFamily.INET;
                    break;
                } else {
                    family = StandardProtocolFamily.INET6;
                }
            }
        }
        if (inf == null) {
            // No usable interface found so just skip the test
            return;
        }

        DefaultNioDatagramChannelConfig config = new DefaultNioDatagramChannelConfig(DatagramChannel.open(family));

        config.setNetworkInterface(inf);
        assertEquals(inf, config.getNetworkInterface());

        InetAddress localhost = inf.getInetAddresses().nextElement();
        config.setInterface(localhost);
        assertEquals(localhost, config.getInterface());

        config.setTimeToLive(100);
        assertEquals(100, config.getTimeToLive());

        config.setLoopbackModeDisabled(false);
        assertFalse(config.isLoopbackModeDisabled());
    }
}
