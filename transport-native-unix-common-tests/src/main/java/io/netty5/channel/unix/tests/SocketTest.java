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
package io.netty5.channel.unix.tests;

import io.netty5.channel.unix.Buffer;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.Socket;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.util.internal.PlatformDependent.addressSize;
import static io.netty5.util.internal.PlatformDependent.getLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class SocketTest<T extends Socket> {
    @AutoClose
    protected T socket;

    protected abstract T newSocket();

    @BeforeEach
    public void setup() {
        socket = newSocket();
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
        ByteBuffer buffer = Buffer.allocateDirectWithNativeOrder(4);
        buffer.putInt(1).flip();
        socket.setRawOpt(level(), optname(), buffer);

        ByteBuffer out = ByteBuffer.allocate(4);
        socket.getRawOpt(level(), optname(), out);
        assertFalse(out.hasRemaining());

        out.flip();
        assertNotEquals(ByteBuffer.allocate(0), out);
    }

    protected int level() {
        throw new TestAbortedException("Not supported");
    }

    protected int optname() {
        throw new TestAbortedException("Not supported");
    }

    /**
     * This is a nested test because we cannot test {@link IovArray} without
     * concrete native bindings.
     */
    @Nested
    class IovArrayTest {
        @BeforeEach
        void requireUnsafe() {
            // We need this to check the contents of the native memory.
            assumeTrue(PlatformDependent.hasUnsafe());
            assumeTrue(Long.BYTES == addressSize());
        }

        @Test
        void addingAndCompletingBuffers() {
            IovArray iovs = new IovArray();
            try (var writable = offHeapAllocator().allocate(32).writeLong(0x0102030405060708L);
                 var readable = writable.readSplit(16)) {
                iovs.addReadable(readable);
                assertThat(iovs.count()).isEqualTo(1);
                assertThat(iovs.size()).isEqualTo(8);

                iovs.addWritable(writable);
                assertThat(iovs.count()).isEqualTo(2);
                assertThat(iovs.size()).isEqualTo(24);

                long addr = iovs.memoryAddress(0);
                try (var itr = readable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().readableNativeAddress());
                    assertThat(getLong(addr + addressSize())).isEqualTo(Long.BYTES);
                }
                addr = iovs.memoryAddress(1);
                try (var itr = writable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().writableNativeAddress());
                    assertThat(getLong(addr + addressSize())).isEqualTo(2 * Long.BYTES);
                }

                assertFalse(iovs.completeBytes(4));

                assertThat(iovs.count()).isEqualTo(2);
                assertThat(iovs.size()).isEqualTo(20);
                addr = iovs.memoryAddress(0);
                try (var itr = readable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().readableNativeAddress() + 4);
                    assertThat(getLong(addr + addressSize())).isEqualTo(Long.BYTES - 4);
                }
                addr = iovs.memoryAddress(1);
                try (var itr = writable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().writableNativeAddress());
                    assertThat(getLong(addr + addressSize())).isEqualTo(2 * Long.BYTES);
                }

                assertFalse(iovs.completeBytes(4));

                assertThat(iovs.count()).isEqualTo(1);
                assertThat(iovs.size()).isEqualTo(16);
                addr = iovs.memoryAddress(0);
                try (var itr = writable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().writableNativeAddress());
                    assertThat(getLong(addr + addressSize())).isEqualTo(2 * Long.BYTES);
                }

                assertFalse(iovs.completeBytes(8));

                assertThat(iovs.count()).isEqualTo(1);
                assertThat(iovs.size()).isEqualTo(8);
                addr = iovs.memoryAddress(0);
                try (var itr = writable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().writableNativeAddress() + 8);
                    assertThat(getLong(addr + addressSize())).isEqualTo(Long.BYTES);
                }

                assertTrue(iovs.completeBytes(8));

                assertThat(iovs.count()).isZero();
                assertThat(iovs.size()).isZero();

                iovs.addReadable(readable);
                iovs.addWritable(writable);
                assertThat(iovs.count()).isEqualTo(2);
                assertThat(iovs.size()).isEqualTo(24);

                assertFalse(iovs.completeBytes(12));

                assertThat(iovs.count()).isEqualTo(1);
                assertThat(iovs.size()).isEqualTo(12);
                addr = iovs.memoryAddress(0);
                try (var itr = writable.forEachComponent()) {
                    assertThat(getLong(addr)).isEqualTo(itr.first().writableNativeAddress() + 4);
                    assertThat(getLong(addr + addressSize())).isEqualTo(12);
                }

                assertTrue(iovs.completeBytes(12));

                assertThat(iovs.count()).isZero();
                assertThat(iovs.size()).isZero();
            }
        }
    }
}
