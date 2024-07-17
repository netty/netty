/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.unix;

import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class NativeInetAddressTest {

    @Test
    public void testAddressNotIncludeScopeId() {
        int port = 80;
        ByteBuffer buffer = ByteBuffer.wrap(new byte[24]);
        buffer.put(new byte[] {'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'});
        buffer.putInt(0);
        buffer.putInt(port);
        InetSocketAddress address = NativeInetAddress.address(buffer.array(), 0, buffer.capacity());
        assertEquals(port, address.getPort());
        assertInstanceOf(Inet6Address.class, address.getAddress());
        assertFalse(address.getAddress().isLinkLocalAddress());
        assertEquals("3030:3030:3030:3030:3030:3030:3030:3031", address.getAddress().getHostName());
    }

    @Test
    public void testLinkOnlyAddressIncludeScopeId() {
        int port = 80;
        ByteBuffer buffer = ByteBuffer.wrap(new byte[24]);
        buffer.put(new byte[] {
                (byte) 0xfe, (byte) (byte) 0x80, '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1'});
        buffer.putInt(0);
        buffer.putInt(port);
        InetSocketAddress address = NativeInetAddress.address(buffer.array(), 0, buffer.capacity());
        assertEquals(port, address.getPort());
        assertInstanceOf(Inet6Address.class, address.getAddress());
        assertTrue(address.getAddress().isLinkLocalAddress());
        assertEquals("fe80:3030:3030:3030:3030:3030:3030:3031%0", address.getAddress().getHostName());
    }
}
