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
package io.netty.handler.codec.socksx.v5;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultSocks5CommandResponseTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        try {
            new DefaultSocks5CommandResponse(null, Socks5AddressType.DOMAIN);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
        try {
            new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, null);
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    /**
     * Verifies content of the response when domain is not specified.
     */
    @Test
    public void testEmptyDomain() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN);
        assertNull(socks5CmdResponse.bndAddr());
        assertEquals(0, socks5CmdResponse.bndPort());

        ByteBuf buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse);
        byte[] expected = {
                0x05, // version
                0x00, // success reply
                0x00, // reserved
                0x03, // address type domain
                0x00, // length of domain
                0x00, // port value
                0x00
        };
        assertByteBufEquals(expected, buffer);
        buffer.release();
    }

    /**
     * Verifies content of the response when IPv4 address is specified.
     */
    @Test
    public void testIPv4Host() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0.1", 80);
        assertEquals("127.0.0.1", socks5CmdResponse.bndAddr());
        assertEquals(80, socks5CmdResponse.bndPort());

        ByteBuf buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse);
        byte[] expected = {
                0x05, // version
                0x00, // success reply
                0x00, // reserved
                0x01, // address type IPv4
                0x7F, // address 127.0.0.1
                0x00,
                0x00,
                0x01,
                0x00, // port
                0x50
                };
        assertByteBufEquals(expected, buffer);
        buffer.release();
    }

    /**
     * Verifies that empty domain is allowed Response.
     */
    @Test
    public void testEmptyBoundAddress() {
        Socks5CommandResponse socks5CmdResponse = new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.DOMAIN, "", 80);
        assertEquals("", socks5CmdResponse.bndAddr());
        assertEquals(80, socks5CmdResponse.bndPort());

        ByteBuf buffer = Socks5CommonTestUtils.encodeServer(socks5CmdResponse);
        byte[] expected = {
                0x05, // version
                0x00, // success reply
                0x00, // reserved
                0x03, // address type domain
                0x00, // domain length
                0x00, // port
                0x50
        };
        assertByteBufEquals(expected, buffer);
        buffer.release();
    }

    /**
     * Verifies that Response cannot be constructed with invalid IP.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBoundAddress() {
        new DefaultSocks5CommandResponse(
                Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 1000);
    }

    private static void assertByteBufEquals(byte[] expected, ByteBuf actual) {
        byte[] actualBytes = new byte[actual.readableBytes()];
        actual.readBytes(actualBytes);
        assertEquals("Generated response has incorrect length", expected.length, actualBytes.length);
        assertArrayEquals("Generated response differs from expected", expected, actualBytes);
    }

    @Test
    public void testValidPortRange() {
        try {
            new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }

        try {
            new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4, "127.0.0", 65536);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
