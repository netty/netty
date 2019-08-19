/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.tests.SocketTest;
import io.netty.channel.unix.tests.UnixTestUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class KQueueSocketTest extends SocketTest<BsdSocket> {
    @BeforeClass
    public static void loadJNI() {
        assumeTrue(KQueue.isAvailable());
    }

    @Test
    public void testPeerCreds() throws IOException {
        BsdSocket s1 = BsdSocket.newSocketDomain();
        BsdSocket s2 = BsdSocket.newSocketDomain();

        try {
            DomainSocketAddress dsa = UnixTestUtils.newSocketAddress();
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
            DomainSocketAddress dsa = UnixTestUtils.newSocketAddress();
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

    @Override
    protected BsdSocket newSocket() {
        return BsdSocket.newSocketStream();
    }
}
