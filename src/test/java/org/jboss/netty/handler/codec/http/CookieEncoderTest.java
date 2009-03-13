/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class CookieEncoderTest {
    @Test
    public void testEncodingSingleCookieV0() {
        String result = "myCookie=myValue;expires=XXX;path=/apathsomewhere;domain=.adomainsomewhere;secure;";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder();
        encoder.addCookie(cookie);
        cookie.setComment("this is a comment");
        cookie.setCommentUrl("http://aurl.com");
        cookie.setDomain(".adomainsomewhere");
        cookie.setDiscard(true);
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setPorts(80, 8080);
        cookie.setSecure(true);
        String encodedCookie = encoder.encode();
        result = result.replace("XXX", new CookieDateFormat().format(new Date(System.currentTimeMillis() + 50000)));
        assertEquals(result, encodedCookie);
    }
    @Test
    public void testEncodingSingleCookieV1() {
        String result = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder();
        encoder.addCookie(cookie);
        cookie.setVersion(1);
        cookie.setComment("this is a comment");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(50);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);
        String encodedCookie = encoder.encode();
        assertEquals(result, encodedCookie);
    }
    @Test
    public void testEncodingSingleCookieV2() {
        String result = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;commentURL=\"http://aurl.com\";port=\"80,8080\";discard;";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        CookieEncoder encoder = new CookieEncoder();
        encoder.addCookie(cookie);
        cookie.setVersion(1);
        cookie.setComment("this is a comment");
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
        String c1 = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;secure;comment=this is a comment;version=1;commentURL=\"http://aurl.com\";port=\"80,8080\";discard;";
        String c2 = "myCookie2=myValue2;path=/anotherpathsomewhere;domain=.anotherdomainsomewhere;comment=this is another comment;version=1;commentURL=\"http://anotherurl.com\";";
        String c3 = "myCookie3=myValue3;version=1;";
        CookieEncoder encoder = new CookieEncoder();
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setVersion(1);
        cookie.setComment("this is a comment");
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
        cookie2.setComment("this is another comment");
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
