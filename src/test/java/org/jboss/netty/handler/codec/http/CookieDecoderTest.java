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

import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class CookieDecoderTest {
    @Test
    public void testDecodingSingleCookieV0() {
        String cookieString = "myCookie=myValue;expires=XXX;path=/apathsomewhere;domain=.adomainsomewhere;secure;";
        cookieString = cookieString.replace("XXX", new CookieDateFormat().format(new Date(System.currentTimeMillis() + 50000)));

        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.getValue());
        assertNull(cookie.getComment());
        assertNull(cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertFalse(cookie.isDiscard());

        boolean fail = true;
        for (int i = 40; i <= 60; i ++) {
            if (cookie.getMaxAge() == i) {
                fail = false;
                break;
            }
        }
        if (fail) {
            fail("expected: 50, actual: " + cookie.getMaxAge());
        }

        assertEquals("/apathsomewhere", cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertTrue(cookie.isSecure());
        assertEquals(0, cookie.getVersion());
    }

    @Test
    public void testDecodingSingleCookieV0ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=0;commentURL=http://aurl.com;port=\"80,8080\";discard;";
        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.getValue());
        assertNull(cookie.getComment());
        assertNull(cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertFalse(cookie.isDiscard());
        assertEquals(50, cookie.getMaxAge());
        assertEquals("/apathsomewhere", cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertTrue(cookie.isSecure());
        assertEquals(0, cookie.getVersion());
    }
    @Test
    public void testDecodingSingleCookieV1() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;";
        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertEquals("myValue", cookie.getValue());
        assertNotNull(cookie);
        assertEquals("this is a comment", cookie.getComment());
        assertNull(cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertFalse(cookie.isDiscard());
        assertEquals(50, cookie.getMaxAge());
        assertEquals("/apathsomewhere", cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertTrue(cookie.isSecure());
        assertEquals(1, cookie.getVersion());
    }

    @Test
    public void testDecodingSingleCookieV1ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;commentURL=http://aurl.com;port='80,8080';discard;";
        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.getValue());
        assertEquals("this is a comment", cookie.getComment());
        assertNull(cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertFalse(cookie.isDiscard());
        assertEquals(50, cookie.getMaxAge());
        assertEquals("/apathsomewhere", cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertTrue(cookie.isSecure());
        assertEquals(1, cookie.getVersion());
    }
    @Test
    public void testDecodingSingleCookieV2() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=2;commentURL=http://aurl.com;port=\"80,8080\";discard;";
        CookieDecoder cookieDecoder = new CookieDecoder();
        Set<Cookie> cookies = cookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.getValue());
        assertEquals("this is a comment", cookie.getComment());
        assertEquals("http://aurl.com", cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertTrue(cookie.isDiscard());
        assertEquals(50, cookie.getMaxAge());
        assertEquals("/apathsomewhere", cookie.getPath());
        assertEquals(2, cookie.getPorts().size());
        assertTrue(cookie.getPorts().contains(80));
        assertTrue(cookie.getPorts().contains(8080));
        assertTrue(cookie.isSecure());
        assertEquals(2, cookie.getVersion());
    }


    @Test
    public void testDecodingMultipleCookies() {
        String c1 = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=2;commentURL=\"http://aurl.com\";port='80,8080';discard;";
        String c2 = "myCookie2=myValue2;max-age=0;path=/anotherpathsomewhere;domain=.anotherdomainsomewhere;comment=this is another comment;version=2;commentURL=http://anotherurl.com;";
        String c3 = "myCookie3=myValue3;max-age=0;version=2;";
        CookieDecoder decoder = new CookieDecoder();

        Set<Cookie> cookies = decoder.decode(c1 + c2 + c3);
        assertEquals(3, cookies.size());
        Iterator<Cookie> it = cookies.iterator();
        Cookie cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.getValue());
        assertEquals("this is a comment", cookie.getComment());
        assertEquals("http://aurl.com", cookie.getCommentUrl());
        assertEquals(".adomainsomewhere", cookie.getDomain());
        assertTrue(cookie.isDiscard());
        assertEquals(50, cookie.getMaxAge());
        assertEquals("/apathsomewhere", cookie.getPath());
        assertEquals(2, cookie.getPorts().size());
        assertTrue(cookie.getPorts().contains(80));
        assertTrue(cookie.getPorts().contains(8080));
        assertTrue(cookie.isSecure());
        assertEquals(2, cookie.getVersion());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue2", cookie.getValue());
        assertEquals("this is another comment", cookie.getComment());
        assertEquals("http://anotherurl.com", cookie.getCommentUrl());
        assertEquals(".anotherdomainsomewhere", cookie.getDomain());
        assertFalse(cookie.isDiscard());
        assertEquals(0, cookie.getMaxAge());
        assertEquals("/anotherpathsomewhere", cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertFalse(cookie.isSecure());
        assertEquals(2, cookie.getVersion());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue3", cookie.getValue());
        assertNull( cookie.getComment());
        assertNull(cookie.getCommentUrl());
        assertNull(cookie.getDomain());
        assertFalse(cookie.isDiscard());
        assertEquals(0, cookie.getMaxAge());
        assertNull(cookie.getPath());
        assertTrue(cookie.getPorts().isEmpty());
        assertFalse(cookie.isSecure());
        assertEquals(2, cookie.getVersion());
    }

    @Test
    public void testDecodingClientSideCookies() {
        String source = "$Version=\"1\"; " +
                "Part_Number=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; " +
                "Part_Number=\"Rocket_Launcher_0001\"; $Path=\"/acme\"";

        Set<Cookie> cookies = new CookieDecoder().decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("Part_Number", c.getName());
        assertEquals("Rocket_Launcher_0001", c.getValue());
        assertEquals("/acme", c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertNull(c.getDomain());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(-1, c.getMaxAge());

        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("Part_Number", c.getName());
        assertEquals("Riding_Rocket_0023", c.getValue());
        assertEquals("/acme/ammo", c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertNull(c.getDomain());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(-1, c.getMaxAge());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingCommaSeparatedClientSideCookies() {
        String source =
            "$Version=\"1\"; session_id=\"1234\", " +
            "$Version=\"1\"; session_id=\"1111\"; $Domain=\".cracker.edu\"";

        Set<Cookie> cookies = new CookieDecoder().decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("session_id", c.getName());
        assertEquals("1234", c.getValue());
        assertNull(c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertNull(c.getDomain());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(-1, c.getMaxAge());

        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("session_id", c.getName());
        assertEquals("1111", c.getValue());
        assertEquals(".cracker.edu", c.getDomain());
        assertNull(c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(-1, c.getMaxAge());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingQuotedCookie() {
        String source =
            "a=\"\"," +
            "b=\"1\"," +
            "c=\"\\\"1\\\"2\\\"\"," +
            "d=\"1\\\"2\\\"3\"," +
            "e=\"\\\"\\\"\"," +
            "f=\"1\\\"\\\"2\"," +
            "g=\"\\\\\"";

        Set<Cookie> cookies = new CookieDecoder().decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals("a", c.getName());
        assertEquals("", c.getValue());

        c = it.next();
        assertEquals("b", c.getName());
        assertEquals("1", c.getValue());

        c = it.next();
        assertEquals("c", c.getName());
        assertEquals("\"1\"2\"", c.getValue());

        c = it.next();
        assertEquals("d", c.getName());
        assertEquals("1\"2\"3", c.getValue());

        c = it.next();
        assertEquals("e", c.getName());
        assertEquals("\"\"", c.getValue());

        c = it.next();
        assertEquals("f", c.getName());
        assertEquals("1\"\"2", c.getValue());

        c = it.next();
        assertEquals("g", c.getName());
        assertEquals("\\", c.getValue());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingGoogleAnalyticsCookie() {
        String source =
            "ARPT=LWUKQPSWRTUN04CKKJI; " +
            "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; " +
            "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; " +
            "__utmb=48461872.13.10.1258140131; __utmc=48461872; " +
            "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html";
        Set<Cookie> cookies = new CookieDecoder().decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals("__utma", c.getName());
        assertEquals("48461872.1094088325.1258140131.1258140131.1258140131.1", c.getValue());

        c = it.next();
        assertEquals("__utmb", c.getName());
        assertEquals("48461872.13.10.1258140131", c.getValue());

        c = it.next();
        assertEquals("__utmc", c.getName());
        assertEquals("48461872", c.getValue());

        c = it.next();
        assertEquals("__utmz", c.getName());
        assertEquals("48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html", c.getValue());

        c = it.next();
        assertEquals("ARPT", c.getName());
        assertEquals("LWUKQPSWRTUN04CKKJI", c.getValue());

        c = it.next();
        assertEquals("kw-2E343B92-B097-442c-BFA5-BE371E0325A2", c.getName());
        assertEquals("unfinished furniture", c.getValue());

        assertFalse(it.hasNext());
    }
}
