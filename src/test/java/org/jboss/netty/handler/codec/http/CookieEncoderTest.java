/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http;

import static org.junit.Assert.*;

import java.text.DateFormat;
import java.util.Date;

import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class CookieEncoderTest {
    @Test
    public void testEncodingSingleCookieV0() {
        String result = "myCookie=myValue;Expires=XXX;Path=/apathsomewhere;Domain=.adomainsomewhere;Secure";
        DateFormat df = new CookieDateFormat();
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder(true);
        encoder.addCookie(cookie);
        cookie.setComment("this is a Comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);

        String encodedCookie = encoder.encode();

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
        String result = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Comment=\"this is a Comment\";Version=1";
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
        assertEquals(result, encodedCookie);
    }

    @Test
    public void testEncodingSingleCookieV2() {
        String result = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Comment=\"this is a Comment\";Version=1;CommentURL=\"http://aurl.com\";Port=\"80,8080\";Discard";
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
        assertEquals(result, encodedCookie);
    }

    @Test
    public void testEncodingMultipleCookies() {
        String c1 = "myCookie=myValue;Max-Age=50;Path=\"/apathsomewhere\";Domain=.adomainsomewhere;Secure;Comment=\"this is a Comment\";Version=1;CommentURL=\"http://aurl.com\";Port=\"80,8080\";Discard;";
        String c2 = "myCookie2=myValue2;Path=\"/anotherpathsomewhere\";Domain=.anotherdomainsomewhere;Comment=\"this is another Comment\";Version=1;CommentURL=\"http://anotherurl.com\";";
        String c3 = "myCookie3=myValue3;Version=1";
        CookieEncoder encoder = new CookieEncoder(true);
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
        assertEquals(c1 + c2 + c3, encodedCookie);
    }
}
