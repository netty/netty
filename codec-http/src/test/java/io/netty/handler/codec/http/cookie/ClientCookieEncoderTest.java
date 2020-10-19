/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.cookie;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClientCookieEncoderTest {

    @Test
    public void testEncodingMultipleClientCookies() {
        String c1 = "myCookie=myValue";
        String c2 = "myCookie2=myValue2";
        String c3 = "myCookie3=myValue3";
        Cookie cookie1 = new DefaultCookie("myCookie", "myValue");
        cookie1.setDomain(".adomainsomewhere");
        cookie1.setMaxAge(50);
        cookie1.setPath("/apathsomewhere");
        cookie1.setSecure(true);
        Cookie cookie2 = new DefaultCookie("myCookie2", "myValue2");
        cookie2.setDomain(".anotherdomainsomewhere");
        cookie2.setPath("/anotherpathsomewhere");
        cookie2.setSecure(false);
        Cookie cookie3 = new DefaultCookie("myCookie3", "myValue3");
        String encodedCookie = ClientCookieEncoder.STRICT.encode(cookie1, cookie2, cookie3);
        // Cookies should be sorted into decreasing order of path length, as per RFC6265.
        // When no path is provided, we assume maximum path length (so cookie3 comes first).
        assertEquals(c3 + "; " + c2 + "; " + c1, encodedCookie);
    }

    @Test
    public void testWrappedCookieValue() {
        ClientCookieEncoder.STRICT.encode(new DefaultCookie("myCookie", "\"foo\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectCookieValueWithSemicolon() {
        ClientCookieEncoder.STRICT.encode(new DefaultCookie("myCookie", "foo;bar"));
    }

    @Test
    public void testComparatorForSamePathLength() {
        Cookie cookie = new DefaultCookie("test", "value");
        cookie.setPath("1");

        Cookie cookie2 = new DefaultCookie("test", "value");
        cookie2.setPath("2");

        assertEquals(0, ClientCookieEncoder.COOKIE_COMPARATOR.compare(cookie, cookie2));
        assertEquals(0, ClientCookieEncoder.COOKIE_COMPARATOR.compare(cookie2, cookie));
    }
}
