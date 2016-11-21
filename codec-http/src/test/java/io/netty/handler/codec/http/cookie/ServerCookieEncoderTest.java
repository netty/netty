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
package io.netty.handler.codec.http.cookie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.netty.handler.codec.DateFormatter;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class ServerCookieEncoderTest {

    @Test
    public void testEncodingSingleCookieV0() throws ParseException {

        int maxAge = 50;

        String result =
                "myCookie=myValue; Max-Age=50; Expires=(.+?); Path=/apathsomewhere; Domain=.adomainsomewhere; Secure";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(maxAge);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);

        String encodedCookie = ServerCookieEncoder.STRICT.encode(cookie);

        Matcher matcher = Pattern.compile(result).matcher(encodedCookie);
        assertTrue(matcher.find());
        Date expiresDate = DateFormatter.parseHttpDate(matcher.group(1));
        long diff = (expiresDate.getTime() - System.currentTimeMillis()) / 1000;
        // 2 secs should be fine
        assertTrue(Math.abs(diff - maxAge) <= 2);
    }

    @Test
    public void testEncodingWithNoCookies() {
        String encodedCookie1 = ClientCookieEncoder.STRICT.encode();
        List<String> encodedCookie2 = ServerCookieEncoder.STRICT.encode();
        assertNull(encodedCookie1);
        assertNotNull(encodedCookie2);
        assertTrue(encodedCookie2.isEmpty());
    }

    @Test
    public void testEncodingMultipleCookiesStrict() {
        List<String> result = new ArrayList<String>();
        result.add("cookie2=value2");
        result.add("cookie1=value3");
        Cookie cookie1 = new DefaultCookie("cookie1", "value1");
        Cookie cookie2 = new DefaultCookie("cookie2", "value2");
        Cookie cookie3 = new DefaultCookie("cookie1", "value3");
        List<String> encodedCookies = ServerCookieEncoder.STRICT.encode(cookie1, cookie2, cookie3);
        assertEquals(result, encodedCookies);
    }

    @Test
    public void illegalCharInCookieNameMakesStrictEncoderThrowsException() {
        Set<Character> illegalChars = new HashSet<Character>();
        // CTLs
        for (int i = 0x00; i <= 0x1F; i++) {
            illegalChars.add((char) i);
        }
        illegalChars.add((char) 0x7F);
        // separators
        for (char c : new char[] { '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']',
                '?', '=', '{', '}', ' ', '\t' }) {
            illegalChars.add(c);
        }

        int exceptions = 0;

        for (char c : illegalChars) {
            try {
                ServerCookieEncoder.STRICT.encode(new DefaultCookie("foo" + c + "bar", "value"));
            } catch (IllegalArgumentException e) {
                exceptions++;
            }
        }

        assertEquals(illegalChars.size(), exceptions);
    }

    @Test
    public void illegalCharInCookieValueMakesStrictEncoderThrowsException() {
        Set<Character> illegalChars = new HashSet<Character>();
        // CTLs
        for (int i = 0x00; i <= 0x1F; i++) {
            illegalChars.add((char) i);
        }
        illegalChars.add((char) 0x7F);
        // whitespace, DQUOTE, comma, semicolon, and backslash
        for (char c : new char[] { ' ', '"', ',', ';', '\\' }) {
            illegalChars.add(c);
        }

        int exceptions = 0;

        for (char c : illegalChars) {
            try {
                ServerCookieEncoder.STRICT.encode(new DefaultCookie("name", "value" + c));
            } catch (IllegalArgumentException e) {
                exceptions++;
            }
        }

        assertEquals(illegalChars.size(), exceptions);
    }

    @Test
    public void testEncodingMultipleCookiesLax() {
        List<String> result = new ArrayList<String>();
        result.add("cookie1=value1");
        result.add("cookie2=value2");
        result.add("cookie1=value3");
        Cookie cookie1 = new DefaultCookie("cookie1", "value1");
        Cookie cookie2 = new DefaultCookie("cookie2", "value2");
        Cookie cookie3 = new DefaultCookie("cookie1", "value3");
        List<String> encodedCookies = ServerCookieEncoder.LAX.encode(cookie1, cookie2, cookie3);
        assertEquals(result, encodedCookies);
    }
}
