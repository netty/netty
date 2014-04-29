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
import static org.junit.Assert.*;


public class SocksCmdRequestTest extends AbstractSocksMessageTest {
    @Test
    public void testConstructorParamsAreNotNull() {
        // Socks4 constructors
        try {
            new SocksCmdRequest(null, 0, null);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.UNKNOWN, 0, null);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        // Socks5 constructors
        try {
            new SocksCmdRequest(null, SocksAddressType.UNKNOWN, "", 0);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.UNKNOWN, null, "", 0);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.UNKNOWN, SocksAddressType.UNKNOWN, null, 0);
        } catch (Exception e) {
            assertNullPointerException(e);
        }

        assertExceptionCounter(5);
    }

    @Test
    public void testCorrectCmdType() {
        try {
            new SocksCmdRequest(SocksCmdType.UNKNOWN, 0, "54.54.111.253");
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.UDP, 0, "54.54.111.253");
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        new SocksCmdRequest(SocksCmdType.CONNECT, 1, "54.54.111.253");
        new SocksCmdRequest(SocksCmdType.BIND, 1, "54.54.111.253");

        assertExceptionCounter(2);
    }

    @Test
    public void testIPv4CorrectAddress() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.IPv4, "54.54.1111.253", 0);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.BIND, 0, "54.54.1111.253");
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        assertExceptionCounter(2);
    }

    @Test
    public void testIPv6CorrectAddress() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.IPv6, "xxx:xxx:xxx", 0);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        assertExceptionCounter(1);
    }

    @Test
    public void testIDNNotExceeds255CharsLimit() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                            "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                            "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                            "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή", 0
            );
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        assertExceptionCounter(1);
    }

    @Test
    public void testValidPortRange() {
        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", -1);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        try {
            new SocksCmdRequest(SocksCmdType.BIND, SocksAddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", 65536);
        } catch (Exception e) {
            assertIllegalArgumentException(e);
        }

        assertExceptionCounter(2);
    }

    @Test
    public void testCorrectEncode() {
        SocksCmdRequest request = new SocksCmdRequest(SocksCmdType.CONNECT, 25, "54.54.111.253");
        assertEquals("54.54.111.253", request.host());
        assertEquals(25, request.port());
        ByteBuf buffer = Unpooled.buffer(20);
        request.encodeAsByteBuf(buffer);
        byte[] expected = {
                0x04,            // version
                0x01,            // connect
                0x00,            // port
                0x19,
                0x36,            // address 54.54.111.253
                0x36,
                0x6F,
                (byte) 0xFD,
                0x20,            // userId _
                0x00
        };
        assertByteBufEquals(expected, buffer);
    }
}
