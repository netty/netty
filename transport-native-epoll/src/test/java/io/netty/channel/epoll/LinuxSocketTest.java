/*
 * Copyright 2020 The Netty Project
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

import io.netty.channel.socket.TunAddress;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.Socket;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class LinuxSocketTest {
    @BeforeAll
    public static void loadJNI() {
        Epoll.ensureAvailability();
    }

    @Test
    public void testBindNonIpv6SocketToInet6AddressThrows() throws Exception {
        final LinuxSocket socket = LinuxSocket.newSocketStream(false);
        try {
            assertThrows(IOException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    socket.bind(new InetSocketAddress(InetAddress.getByAddress(
                            new byte[]{'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'}),
                            0));
                }
            });
        } finally {
            socket.close();
        }
    }

    @Test
    public void testConnectNonIpv6SocketToInet6AddressThrows() throws Exception {
        final LinuxSocket socket = LinuxSocket.newSocketStream(false);
        try {
            assertThrows(IOException.class,
                    new Executable() {
                        @Override
                        public void execute() throws Throwable {
                            socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{
                                    '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'}),
                                    1234));
                        }
                    });
        } finally {
            socket.close();
        }
    }

    @Test
    public void testUnixDomainSocketTooLongPathFails() throws IOException {
        // Most systems has a limit for UDS path of 108, 255 is generally too long.
        StringBuilder socketPath = new StringBuilder("/tmp/");
        while (socketPath.length() < 255) {
            socketPath.append(UUID.randomUUID());
        }

        final DomainSocketAddress domainSocketAddress = new DomainSocketAddress(
            socketPath.toString());
        final Socket socket = Socket.newSocketDomain();
        try {
            Exception exception = Assertions.assertThrows(NativeIoException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    socket.bind(domainSocketAddress);
                }
            });
            Assertions.assertTrue(exception.getMessage().contains("too long"));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testTunBind() throws IOException {
        LinuxSocket tun = null;
        try {
            tun = LinuxSocket.newSocketTun();
            tun.bindTun(new TunAddress(), false);
            assertTrue(true);
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunBindInvalidName() throws IOException {
        LinuxSocket tun = null;
        try {
            tun = LinuxSocket.newSocketTun();
            tun.bindTun(new TunAddress("tooLongTunDeviceName"), false);
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
    public void testTunAddress() throws IOException {
        LinuxSocket tun = null;
        try {
            tun = LinuxSocket.newSocketTun();
            final TunAddress address = tun.bindTun(new TunAddress(), false);
            assertNotNull(address);
            assertThat(address, is(instanceOf(TunAddress.class)));
            assertNotNull(address.ifName());
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunGetMtu() throws IOException {
        LinuxSocket tun = null;
        try {
            tun = LinuxSocket.newSocketTun();
            final TunAddress address = tun.bindTun(new TunAddress(), false);
            final String name = address.ifName();
            assertThat(LinuxSocket.getMtu(name), is(greaterThan(0)));
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }

    @Test
    public void testTunSetMtu() throws IOException {
        LinuxSocket tun = null;
        try {
            tun = LinuxSocket.newSocketTun();
            final TunAddress address = tun.bindTun(new TunAddress(), false);
            final String name = address.ifName();
            LinuxSocket.setMtu(name, 1000);
            assertEquals(1000, LinuxSocket.getMtu(name));
        } finally {
            if (tun != null) {
                tun.close();
            }
        }
    }
}
