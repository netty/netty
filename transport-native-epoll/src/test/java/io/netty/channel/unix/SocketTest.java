/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix;

import io.netty.channel.epoll.Epoll;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SocketTest {

    static {
        Epoll.ensureAvailability();
    }

    private Socket socket;

    @Before
    public void setup() {
        socket = Socket.newSocketStream();
    }

    @After
    public void tearDown() throws IOException {
        socket.close();
    }

    @Test
    public void testKeepAlive() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            assertFalse(socket.isKeepAlive());
            socket.setKeepAlive(true);
            assertTrue(socket.isKeepAlive());
        } finally {
            socket.close();
        }
    }

    @Test
    public void testTcpCork() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            assertFalse(socket.isTcpCork());
            socket.setTcpCork(true);
            assertTrue(socket.isTcpCork());
        } finally {
            socket.close();
        }
    }

    @Test
    public void testTcpNoDelay() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            assertFalse(socket.isTcpNoDelay());
            socket.setTcpNoDelay(true);
            assertTrue(socket.isTcpNoDelay());
        } finally {
            socket.close();
        }
    }

    @Test
    public void testReceivedBufferSize() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            int size = socket.getReceiveBufferSize();
            int newSize = 65535;
            assertTrue(size > 0);
            socket.setReceiveBufferSize(newSize);
            // Linux usually set it to double what is specified
            assertTrue(newSize <= socket.getReceiveBufferSize());
        } finally {
            socket.close();
        }
    }

    @Test
    public void testSendBufferSize() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            int size = socket.getSendBufferSize();
            int newSize = 65535;
            assertTrue(size > 0);
            socket.setSendBufferSize(newSize);
            // Linux usually set it to double what is specified
            assertTrue(newSize <= socket.getSendBufferSize());
        } finally {
            socket.close();
        }
    }

    @Test
    public void testSoLinger() throws Exception {
        Socket socket = Socket.newSocketStream();
        try {
            assertEquals(-1, socket.getSoLinger());
            socket.setSoLinger(10);
            assertEquals(10, socket.getSoLinger());
        } finally {
            socket.close();
        }
    }
}

