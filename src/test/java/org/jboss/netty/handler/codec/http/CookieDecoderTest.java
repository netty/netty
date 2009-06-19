/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.http;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
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
    }
}
