/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ServerCookieEncoderTest {
    @Test
    public void testEncodingSingleCookieV0() {
        String result = "myCookie=myValue; Max-Age=50; Path=/apathsomewhere; Domain=.adomainsomewhere; Secure";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);

        String encodedCookie = ServerCookieEncoder.encode(cookie);
        assertEquals(result, encodedCookie);
    }

    @Test
    public void testEncodingWithNoCookies() {
        String encodedCookie1 = ClientCookieEncoder.encode();
        List<String> encodedCookie2 = ServerCookieEncoder.encode();
        assertNull(encodedCookie1);
        assertNotNull(encodedCookie2);
        assertTrue(encodedCookie2.isEmpty());
    }
}
