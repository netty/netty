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

import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
