/*
 * Copyright 2016 The Netty Project
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

import io.netty.channel.socket.TunAddress;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.tests.SocketTest;
import io.netty.channel.unix.tests.UnixTestUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketAddress;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class KQueueSocketTest extends SocketTest<BsdSocket> {
    @BeforeAll
    public static void loadJNI() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testPeerCreds() throws IOException {
        BsdSocket s1 = BsdSocket.newSocketDomain();
        BsdSocket s2 = BsdSocket.newSocketDomain();

        try {
            DomainSocketAddress dsa = UnixTestUtils.newDomainSocketAddress();
            s1.bind(dsa);
            s1.listen(1);

            assertTrue(s2.connect(dsa));
            byte [] addr = new byte[64];
            s1.accept(addr);
            PeerCredentials pc = s1.getPeerCredentials();
            assertNotEquals(pc.uid(), -1);
        } finally {
            s1.close();
            s2.close();
        }
    }

    @Test
    public void testPeerPID() throws IOException {
        BsdSocket s1 = BsdSocket.newSocketDomain();
        BsdSocket s2 = BsdSocket.newSocketDomain();

        try {
            DomainSocketAddress dsa = UnixTestUtils.newDomainSocketAddress();
            s1.bind(dsa);
            s1.listen(1);

            // PID of client socket is expected to be 0 before connection
            assertEquals(0, s2.getPeerCredentials().pid());
            assertTrue(s2.connect(dsa));
            byte [] addr = new byte[64];
            int clientFd = s1.accept(addr);
            assertNotEquals(-1, clientFd);
            PeerCredentials pc = new BsdSocket(clientFd).getPeerCredentials();
            assertNotEquals(0, pc.pid());
            assertNotEquals(0, s2.getPeerCredentials().pid());
            // Server socket FDs should not have pid field set:
            assertEquals(0, s1.getPeerCredentials().pid());
        } finally {
            s1.close();
            s2.close();
        }
    }

    @Test
    public void testTunBind() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.bindTun(new TunAddress());
            assertTrue(true);
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunBindInvalidName() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.bindTun(new TunAddress("foo123"));
            fail();
        } catch (Exception e) {
            // expected
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunAddressBeforeBind() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.localAddressTun();
            fail();
        } catch (Exception e) {
            // expected
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunAddressAfterBind() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.bindTun(new TunAddress());
            final SocketAddress address = tun.localAddressTun();
            assertNotNull(address);
            assertThat(address, is(instanceOf(TunAddress.class)));
            assertNotNull(((TunAddress) address).ifName());
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunGetMtu() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.bindTun(new TunAddress());
            final String name = ((TunAddress) tun.localAddressTun()).ifName();
            assertThat(tun.getMtu(name), is(greaterThan(0)));
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunSetMtu() throws IOException {
        BsdSocket tun = null;
        try {
            tun = BsdSocket.newSocketTun();
            tun.bindTun(new TunAddress());
            final String name = ((TunAddress) tun.localAddressTun()).ifName();
            tun.setMtu(name, 1000);
            assertEquals(1000, tun.getMtu(name));
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Override
    protected BsdSocket newSocket() {
        return BsdSocket.newSocketStream();
    }

    @Override
    protected int level() {
        // Value for SOL_SOCKET
        // See https://opensource.apple.com/source/xnu/xnu-201/bsd/sys/socket.h.auto.html
        return 0xffff;
    }

    @Override
    protected int optname() {
        // Value for SO_REUSEADDR
        // See https://opensource.apple.com/source/xnu/xnu-201/bsd/sys/socket.h.auto.html
        return 0x0004;
    }
}
