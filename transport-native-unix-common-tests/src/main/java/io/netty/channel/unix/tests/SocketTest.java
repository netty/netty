/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix.tests;

import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.internal.CleanableDirectBuffer;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class SocketTest<T extends Socket> {
    protected T socket;

    protected abstract T newSocket();

    @BeforeEach
    public void setup() {
        socket = newSocket();
    }

    @AfterEach
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

    @Test
    public void testIntOpt() throws IOException {
        socket.setReuseAddress(false);
        socket.setIntOpt(level(), optname(), 1);
        // Anything which is != 0 is considered enabled
        assertNotEquals(0, socket.getIntOpt(level(), optname()));
        socket.setIntOpt(level(), optname(), 0);
        // This should be disabled again
        assertEquals(0, socket.getIntOpt(level(), optname()));
    }

    @Test
    public void testRawOpt() throws IOException {
        CleanableDirectBuffer cleanableDirectBuffer = Buffer.allocateDirectBufferWithNativeOrder(4);
        ByteBuffer buffer = cleanableDirectBuffer.buffer();
        buffer.putInt(1).flip();
        socket.setRawOpt(level(), optname(), buffer);

        ByteBuffer out = ByteBuffer.allocate(4);
        socket.getRawOpt(level(), optname(), out);
        assertFalse(out.hasRemaining());

        out.flip();
        assertNotEquals(ByteBuffer.allocate(0), out);
        cleanableDirectBuffer.clean();
    }

    protected int level() {
        throw new TestAbortedException("Not supported");
    }

    protected int optname() {
        throw new TestAbortedException("Not supported");
    }

    @Test
    public void testBindDomainSocketDoesNotRemoveRegularFile() throws Exception {
        File file = PlatformDependent.createTempFile("NETTY", "UDS", null);
        try {
            byte[] content = "do not delete".getBytes(StandardCharsets.US_ASCII);
            Files.write(file.toPath(), content);

            Socket domainSocket = Socket.newSocketDomain();
            try {
                assertThrows(IOException.class, () -> domainSocket.bind(new DomainSocketAddress(file)));
            } finally {
                domainSocket.close();
            }
            assertTrue(file.isFile());
            assertArrayEquals(content, Files.readAllBytes(file.toPath()));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testBindDomainSocketReplacesStaleSocket() throws Exception {
        DomainSocketAddress address = UnixTestUtils.newDomainSocketAddress();
        File file = new File(address.path());
        try {
            Socket first = Socket.newSocketDomain();
            try {
                first.bind(address);
            } finally {
                first.close();
            }
            // The socket inode is left behind after close.
            assertTrue(file.exists());

            Socket second = Socket.newSocketDomain();
            try {
                second.bind(address);
            } finally {
                second.close();
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testDeleteDomainSocketFileOnlyDeletesSockets() throws Exception {
        File regular = PlatformDependent.createTempFile("NETTY", "UDS", null);
        DomainSocketAddress address = UnixTestUtils.newDomainSocketAddress();
        File socketFile = new File(address.path());
        try {
            assertFalse(UnixChannelUtil.deleteDomainSocketFile(regular.getPath()));
            assertTrue(regular.exists());

            Socket domainSocket = Socket.newSocketDomain();
            try {
                domainSocket.bind(address);
            } finally {
                domainSocket.close();
            }
            assertTrue(UnixChannelUtil.deleteDomainSocketFile(address.path()));
            assertFalse(socketFile.exists());
            // Does not exist anymore.
            assertFalse(UnixChannelUtil.deleteDomainSocketFile(address.path()));
        } finally {
            regular.delete();
            socketFile.delete();
        }
    }
}
