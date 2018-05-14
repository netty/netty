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
package io.netty.channel.unix.tests;

import io.netty.channel.unix.Socket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class SocketTest<T extends Socket> {
    protected T socket;

    protected abstract T newSocket();

    @Before
    public void setup() {
        socket = newSocket();
    }

    @After
    public void tearDown() throws IOException {
        socket.close();
    }

    @Test
    public void testKeepAlive() throws Exception {
        assertFalse(socket.isKeepAlive());
        socket.setKeepAlive(true);
        assertTrue(socket.isKeepAlive());
    }

    @Test
    public void testTcpNoDelay() throws Exception {
        assertFalse(socket.isTcpNoDelay());
        socket.setTcpNoDelay(true);
        assertTrue(socket.isTcpNoDelay());
    }

    @Test
    public void testReceivedBufferSize() throws Exception {
        int size = socket.getReceiveBufferSize();
        int newSize = 65535;
        assertTrue(size > 0);
        socket.setReceiveBufferSize(newSize);
        // Linux usually set it to double what is specified
        assertTrue(newSize <= socket.getReceiveBufferSize());
    }

    @Test
    public void testSendBufferSize() throws Exception {
        int size = socket.getSendBufferSize();
        int newSize = 65535;
        assertTrue(size > 0);
        socket.setSendBufferSize(newSize);
        // Linux usually set it to double what is specified
        assertTrue(newSize <= socket.getSendBufferSize());
    }

    @Test
    public void testSoLinger() throws Exception {
        assertEquals(-1, socket.getSoLinger());
        socket.setSoLinger(10);
        assertEquals(10, socket.getSoLinger());
    }

    @Test
    public void testDoubleCloseDoesNotThrow() throws IOException {
        Socket socket = Socket.newSocketStream();
        socket.close();
        socket.close();
    }

    @Test
    public void testTrafficClass() throws IOException {
        // IPTOS_THROUGHPUT
        final int value = 0x08;
        socket.setTrafficClass(value);
        assertEquals(value, socket.getTrafficClass());
    }
}
