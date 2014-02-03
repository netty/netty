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
package io.netty.handler.codec.http;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import org.junit.Test;

public class CookieEncoderTest {
    @Test
    public void testEncodingSingleCookieV0() {
        String result = "myCookie=myValue; Expires=XXX; Path=/apathsomewhere; Domain=.adomainsomewhere; Secure";
        DateFormat df = HttpHeaderDateFormat.get();
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setComment("this is a Comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);

        String encodedCookie = ServerCookieEncoder.encode(cookie);

        long currentTime = System.currentTimeMillis();
        boolean fail = true;
        // +/- 10-second tolerance
        for (int delta = 0; delta <= 20000; delta += 250) {
            if (encodedCookie.equals(result.replace(
                    "XXX", df.format(new Date(currentTime + 40000 + delta))))) {
                fail = false;
                break;
            }
        }

        if (fail) {
            fail("Expected: " + result + ", Actual: " + encodedCookie);
        }
    }

    @Test
    public void testEncodingSingleCookieV1() {
        String result = "myCookie=myValue; Max-Age=50; Path=\"/apathsomewhere\"; " +
                "Domain=.adomainsomewhere; Secure; Comment=\"this is a Comment\"; Version=1";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setVersion(1);
        cookie.setComment("this is a Comment");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);
        String encodedCookie = ServerCookieEncoder.encode(cookie);
        assertEquals(result, encodedCookie);
    }

    @Test
    public void testEncodingSingleCookieV2() {
        String result = "myCookie=myValue; Max-Age=50; Path=\"/apathsomewhere\"; Domain=.adomainsomewhere; " +
                "Secure; Comment=\"this is a Comment\"; Version=1; CommentURL=\"http://aurl.com\"; " +
                "Port=\"80,8080\"; Discard";
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
        String encodedCookie = ServerCookieEncoder.encode(cookie);
        assertEquals(result, encodedCookie);
    }

    @Test
    public void testEncodingMultipleClientCookies() {
        String c1 = "$Version=1; myCookie=myValue; $Path=\"/apathsomewhere\"; " +
                "$Domain=.adomainsomewhere; $Port=\"80,8080\"; ";
        String c2 = "$Version=1; myCookie2=myValue2; $Path=\"/anotherpathsomewhere\"; " +
                "$Domain=.anotherdomainsomewhere; ";
        String c3 = "$Version=1; myCookie3=myValue3";
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
        Cookie cookie2 = new DefaultCookie("myCookie2", "myValue2");
        cookie2.setVersion(1);
        cookie2.setComment("this is another Comment");
        cookie2.setCommentUrl("http://anotherurl.com");
        cookie2.setDomain(".anotherdomainsomewhere");
        cookie2.setDiscard(false);
        cookie2.setPath("/anotherpathsomewhere");
        cookie2.setSecure(false);
        Cookie cookie3 = new DefaultCookie("myCookie3", "myValue3");
        cookie3.setVersion(1);
        String encodedCookie = ClientCookieEncoder.encode(cookie, cookie2, cookie3);
        assertEquals(c1 + c2 + c3, encodedCookie);
    }

    @Test
    public void testEncodingWithNoCookies() {
        String encodedCookie1 = ClientCookieEncoder.encode();
        List<String> encodedCookie2 = ServerCookieEncoder.encode();
        assertNotNull(encodedCookie1);
        assertNotNull(encodedCookie2);
    }
}
