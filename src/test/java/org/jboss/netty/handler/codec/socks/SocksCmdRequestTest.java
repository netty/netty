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
package org.jboss.netty.handler.codec.socks;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

public class SocksCmdRequestTest {
    @Test
    public void testConstructorParamsAreNotNull(){
        try {
            new SocksCmdRequest(null, SocksMessage.AddressType.UNKNOWN, "", 0);
        } catch (Exception e){
            assertTrue(e instanceof NullPointerException);
        }

        try {
            new SocksCmdRequest(SocksMessage.CmdType.UNKNOWN, null, "", 0);
        } catch (Exception e){
            assertTrue(e instanceof NullPointerException);
        }

        try {
            new SocksCmdRequest(SocksMessage.CmdType.UNKNOWN, SocksMessage.AddressType.UNKNOWN, null, 0);
        } catch (Exception e){
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testIPv4CorrectAddress(){
        try {
            new SocksCmdRequest(SocksMessage.CmdType.BIND, SocksMessage.AddressType.IPv4, "54.54.1111.253", 0);
        } catch (Exception e){
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testIPv6CorrectAddress(){
        try {
            new SocksCmdRequest(SocksMessage.CmdType.BIND, SocksMessage.AddressType.IPv6, "xxx:xxx:xxx", 0);
        } catch (Exception e){
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testIDNNotExceeds255CharsLimit(){
        try {
            new SocksCmdRequest(SocksMessage.CmdType.BIND, SocksMessage.AddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή" +
                    "παράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμήπαράδειγμα.δοκιμή", 0);
        } catch (Exception e){
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testValidPortRange(){
        try {
            new SocksCmdRequest(SocksMessage.CmdType.BIND, SocksMessage.AddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", -1);
        } catch (Exception e){
            assertTrue(e instanceof IllegalArgumentException);
        }

        try {
            new SocksCmdRequest(SocksMessage.CmdType.BIND, SocksMessage.AddressType.DOMAIN,
                    "παράδειγμα.δοκιμήπαράδει", 65536);
        } catch (Exception e){
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
