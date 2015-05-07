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
package org.jboss.netty.handler.codec.http;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class CookieEncoderTest {
    @Test
    public void testEncodingSingleCookieV0() throws ParseException {
        int maxAge = 50;
        String result = "myCookie=myValue; Max-Age=" + maxAge + "; Expires=(.+?); Path=/apathsomewhere; "
                + "Domain=.adomainsomewhere; Secure";
        DateFormat df = HttpHeaderDateFormat.get();
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie(cookie);
        cookie.setComment("this is a Comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);

        String encodedCookie = encoder.encode();

        Matcher matcher = Pattern.compile(result).matcher(encodedCookie);
        assertTrue(matcher.find());
        Date expiresDate = HttpHeaderDateFormat.get().parse(matcher.group(1));
        long diff = (expiresDate.getTime() - System.currentTimeMillis()) / 1000;
        // 2 secs should be fine
        assertTrue(Math.abs(diff - maxAge) <= 2);
    }

    @Test
    public void testEncodingSingleCookieV1() throws ParseException {
        int maxAge = 50;
        String result = "myCookie=myValue; Max-Age=" + maxAge + "; Expires=(.+?); Path=/apathsomewhere; "
                + "Domain=.adomainsomewhere; Secure";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie(cookie);
        cookie.setVersion(1);
        cookie.setComment("this is a Comment");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);
        String encodedCookie = encoder.encode();

        Matcher matcher = Pattern.compile(result).matcher(encodedCookie);
        assertTrue(matcher.find());
        Date expiresDate = HttpHeaderDateFormat.get().parse(matcher.group(1));
        long diff = (expiresDate.getTime() - System.currentTimeMillis()) / 1000;
        // 2 secs should be fine
        assertTrue(Math.abs(diff - maxAge) <= 2);
    }

    @Test
    public void testEncodingSingleCookieV2() throws ParseException {
        int maxAge = 50;
        String result = "myCookie=myValue; Max-Age=" + maxAge + "; Expires=(.+?); Path=/apathsomewhere; "
                + "Domain=.adomainsomewhere; Secure";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie(cookie);
        cookie.setVersion(1);
        cookie.setComment("this is a Comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);
        String encodedCookie = encoder.encode();
 
        Matcher matcher = Pattern.compile(result).matcher(encodedCookie);
        assertTrue(matcher.find());
        Date expiresDate = HttpHeaderDateFormat.get().parse(matcher.group(1));
        long diff = (expiresDate.getTime() - System.currentTimeMillis()) / 1000;
        // 2 secs should be fine
        assertTrue(Math.abs(diff - maxAge) <= 2);
    }

    @Test
    public void testEncodingMultipleServerCookies() {
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie("a", "b");
        encoder.addCookie("b", "c");
        try {
            encoder.encode();
            fail();
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testEncodingMultipleClientCookies() {
        String c1 = "myCookie=myValue; ";
        String c2 = "myCookie2=myValue2; ";
        String c3 = "myCookie3=myValue3";
        CookieEncoder encoder = new CookieEncoder(false);
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setVersion(1);
        cookie.setComment("this is a Comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);
        encoder.addCookie(cookie);
        Cookie cookie2 = new DefaultCookie("myCookie2", "myValue2");
        cookie2.setVersion(1);
        cookie2.setComment("this is another Comment");
        cookie2.setCommentUrl("http://anotherurl.com");
        cookie2.setDomain(".anotherdomainsomewhere");
        cookie2.setDiscard(false);
        cookie2.setPath("/anotherpathsomewhere");
        cookie2.setSecure(false);
        encoder.addCookie(cookie2);
        Cookie cookie3 = new DefaultCookie("myCookie3", "myValue3");
        cookie3.setVersion(1);
        encoder.addCookie(cookie3);
        String encodedCookie = encoder.encode();
        assertEquals(c1 +c2 + c3, encodedCookie);
    }
}
