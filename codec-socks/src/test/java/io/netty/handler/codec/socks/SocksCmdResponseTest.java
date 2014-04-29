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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import static io.netty.handler.codec.socks.SocksCmdResponse.NULL;
import static org.junit.Assert.*;

public class SocksCmdResponseTest extends AbstractSocksMessageTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        try {
            new SocksCmdResponse(null);
        } catch (Exception e) {
            assertNullPointerException(e);
        }
        try {
            new SocksCmdResponse(null, SocksAddressType.UNKNOWN);
        } catch (Exception e) {
            assertNullPointerException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.UNASSIGNED, null);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        assertExceptionCounter(3);
    }

    @Test
    public void testCorrectCmdStatus() {
        // for Socks4
        try {
            new SocksCmdResponse(SocksCmdStatus.SUCCESS);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.FAILURE);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.FORBIDDEN);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.NETWORK_UNREACHABLE);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.HOST_UNREACHABLE);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.REFUSED);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.TTL_EXPIRED);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.COMMAND_NOT_SUPPORTED);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.ADDRESS_NOT_SUPPORTED);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        // for Socks5
        try {
            new SocksCmdResponse(SocksCmdStatus.SUCCESS4, SocksAddressType.IPv4);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.FAILURE4, SocksAddressType.IPv4);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.FAILURE4_IDENTD_CONFIRM, SocksAddressType.IPv4);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }
        try {
            new SocksCmdResponse(SocksCmdStatus.FAILURE4_IDENTD_NOT_RUN, SocksAddressType.IPv4);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        assertExceptionCounter(13);
    }

    /**
     * Verifies content of the response when domain is not specified.
     */
    @Test
    public void testEmptyDomain() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.DOMAIN);
        assertNull(socksCmdResponse.host());
        assertEquals(1, socksCmdResponse.port());
        ByteBuf buffer = Unpooled.buffer(20);
        socksCmdResponse.encodeAsByteBuf(buffer);
        byte[] expected = {
                0x05, // version
                0x00, // success reply
                0x00, // reserved
                0x03, // address type domain
                0x01, // length of domain
                0x00, // domain value
                0x00, // port value
                0x01
        };
        assertByteBufEquals(expected, buffer);
    }

    /**
     * Verifies content of the response when IPv4 address is specified.
     */
    @Test
    public void testIPv4Host() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4,
                "127.0.0.1", 80);
        assertEquals("127.0.0.1", socksCmdResponse.host());
        assertEquals(80, socksCmdResponse.port());
        ByteBuf buffer = Unpooled.buffer(20);
        socksCmdResponse.encodeAsByteBuf(buffer);
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
    }

    @Test
    public void testSocks4Success() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS4);
        ByteBuf buffer = Unpooled.buffer(20);
        socksCmdResponse.encodeAsByteBuf(buffer);
        byte[] expected = {
                NULL, // for Socks4 version in response == 0x00 (null byte)
                0x5a, // success reply
                0x11, // reserved 2
                0x22,
                0x33, // reserved 4
                0x44,
                0x55,
                0x66,
        };
        assertByteBufEquals(expected, buffer);
    }

    /**
     * Verifies that empty domain is allowed Response.
     */
    @Test
    public void testEmptyBoundAddress() {
        SocksCmdResponse socksCmdResponse = new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.DOMAIN,
                "", 80);
        assertEquals("", socksCmdResponse.host());
        assertEquals(80, socksCmdResponse.port());
        ByteBuf buffer = Unpooled.buffer(20);
        socksCmdResponse.encodeAsByteBuf(buffer);
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
    }

    /**
     * Verifies that Response cannot be constructed with invalid IP.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidBoundAddress() {
        new SocksCmdResponse(SocksCmdStatus.SUCCESS, SocksAddressType.IPv4, "127.0.0", 1000);
    }
}
