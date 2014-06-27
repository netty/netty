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

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class CookieDecoderTest {
    @Test
    public void testDecodingSingleCookieV0() {
        String cookieString = "myCookie=myValue;expires=XXX;path=/apathsomewhere;domain=.adomainsomewhere;secure;";
        cookieString = cookieString.replace("XXX",
                HttpHeaderDateFormat.get().format(new Date(System.currentTimeMillis() + 50000)));

        Set<Cookie> cookies = CookieDecoder.decode(cookieString);
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
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=0;" +
                "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        Set<Cookie> cookies = CookieDecoder.decode(cookieString);
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
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=1;";
        Set<Cookie> cookies = CookieDecoder.decode(cookieString);
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
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=1;" +
                "commentURL=http://aurl.com;port='80,8080';discard;";
        Set<Cookie> cookies = CookieDecoder.decode(cookieString);
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
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;" +
                "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        Set<Cookie> cookies = CookieDecoder.decode(cookieString);
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
        String c1 = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;" +
                "commentURL=\"http://aurl.com\";port='80,8080';discard;";
        String c2 = "myCookie2=myValue2;max-age=0;path=/anotherpathsomewhere;" +
                "domain=.anotherdomainsomewhere;comment=this is another comment;version=2;" +
                "commentURL=http://anotherurl.com;";
        String c3 = "myCookie3=myValue3;max-age=0;version=2;";

        Set<Cookie> cookies = CookieDecoder.decode(c1 + c2 + c3);
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
        assertNull(cookie.getComment());
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

        Set<Cookie> cookies = CookieDecoder.decode(source);
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
        assertEquals(Long.MIN_VALUE, c.getMaxAge());

        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("Part_Number", c.getName());
        assertEquals("Riding_Rocket_0023", c.getValue());
        assertEquals("/acme/ammo", c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertNull(c.getDomain());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(Long.MIN_VALUE, c.getMaxAge());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingCommaSeparatedClientSideCookies() {
        String source =
            "$Version=\"1\"; session_id=\"1234\", " +
            "$Version=\"1\"; session_id=\"1111\"; $Domain=\".cracker.edu\"";

        Set<Cookie> cookies = CookieDecoder.decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        assertTrue(it.hasNext());
        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("session_id", c.getName());
        assertEquals("1234", c.getValue());
        assertNull(c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertNull(c.getDomain());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(Long.MIN_VALUE, c.getMaxAge());

        assertTrue(it.hasNext());
        c = it.next();
        assertEquals(1, c.getVersion());
        assertEquals("session_id", c.getName());
        assertEquals("1111", c.getValue());
        assertEquals(".cracker.edu", c.getDomain());
        assertNull(c.getPath());
        assertNull(c.getComment());
        assertNull(c.getCommentUrl());
        assertTrue(c.getPorts().isEmpty());
        assertEquals(Long.MIN_VALUE, c.getMaxAge());

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
            "g=\"\\\\\"," +
            "h=\"';,\\x\"";

        Set<Cookie> cookies = CookieDecoder.decode(source);
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

        c = it.next();
        assertEquals("h", c.getName());
        assertEquals("';,\\x", c.getValue());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingGoogleAnalyticsCookie() {
        String source =
            "ARPT=LWUKQPSWRTUN04CKKJI; " +
            "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; " +
            "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; " +
            "__utmb=48461872.13.10.1258140131; __utmc=48461872; " +
            "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|" +
                    "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html";
        Set<Cookie> cookies = CookieDecoder.decode(source);
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
        assertEquals("48461872.1258140131.1.1.utmcsr=overstock.com|" +
                "utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html",
                c.getValue());

        c = it.next();
        assertEquals("ARPT", c.getName());
        assertEquals("LWUKQPSWRTUN04CKKJI", c.getValue());

        c = it.next();
        assertEquals("kw-2E343B92-B097-442c-BFA5-BE371E0325A2", c.getName());
        assertEquals("unfinished furniture", c.getValue());

        assertFalse(it.hasNext());
    }

    @Test
    public void testDecodingLongDates() {
        Calendar cookieDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cookieDate.set(9999, Calendar.DECEMBER, 31, 23, 59, 59);
        long expectedMaxAge = (cookieDate.getTimeInMillis() - System.currentTimeMillis()) / 1000;

        String source = "Format=EU; expires=Fri, 31-Dec-9999 23:59:59 GMT; path=/";

        Set<Cookie> cookies = CookieDecoder.decode(source);

        Cookie c = cookies.iterator().next();
        assertTrue(Math.abs(expectedMaxAge - c.getMaxAge()) < 2);
    }

    @Test
    public void testDecodingValueWithComma() {
        String source = "UserCookie=timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE;" +
                " expires=Sat, 01-Dec-2012 10:53:31 GMT; path=/";

        Set<Cookie> cookies = CookieDecoder.decode(source);

        Cookie c = cookies.iterator().next();
        assertEquals("timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE", c.getValue());
    }

    @Test
    public void testDecodingWeirdNames1() {
        String src = "path=; expires=Mon, 01-Jan-1990 00:00:00 GMT; path=/; domain=.www.google.com";
        Set<Cookie> cookies = CookieDecoder.decode(src);
        Cookie c = cookies.iterator().next();
        assertEquals("path", c.getName());
        assertEquals("", c.getValue());
        assertEquals("/", c.getPath());
    }

    @Test
    public void testDecodingWeirdNames2() {
        String src = "HTTPOnly=";
        Set<Cookie> cookies = CookieDecoder.decode(src);
        Cookie c = cookies.iterator().next();
        assertEquals("HTTPOnly", c.getName());
        assertEquals("", c.getValue());
    }

    @Test
    public void testDecodingValuesWithCommasAndEquals() {
        String src = "A=v=1&lg=en-US,it-IT,it&intl=it&np=1;T=z=E";
        Set<Cookie> cookies = CookieDecoder.decode(src);
        Iterator<Cookie> i = cookies.iterator();
        Cookie c = i.next();
        assertEquals("A", c.getName());
        assertEquals("v=1&lg=en-US,it-IT,it&intl=it&np=1", c.getValue());
        c = i.next();
        assertEquals("T", c.getName());
        assertEquals("z=E", c.getValue());
    }

    @Test
    public void testDecodingLongValue() {
        String longValue =
                "b!!!$Q!!$ha!!<NC=MN(F!!%#4!!<NC=MN(F!!2!d!!!!#=IvZB!!2,F!!!!'=KqtH!!2-9!!!!" +
                "'=IvZM!!3f:!!!!$=HbQW!!3g'!!!!%=J^wI!!3g-!!!!%=J^wI!!3g1!!!!$=HbQW!!3g2!!!!" +
                "$=HbQW!!3g5!!!!%=J^wI!!3g9!!!!$=HbQW!!3gT!!!!$=HbQW!!3gX!!!!#=J^wI!!3gY!!!!" +
                "#=J^wI!!3gh!!!!$=HbQW!!3gj!!!!$=HbQW!!3gr!!!!$=HbQW!!3gx!!!!#=J^wI!!3h!!!!!" +
                "$=HbQW!!3h$!!!!#=J^wI!!3h'!!!!$=HbQW!!3h,!!!!$=HbQW!!3h0!!!!%=J^wI!!3h1!!!!" +
                "#=J^wI!!3h2!!!!$=HbQW!!3h4!!!!$=HbQW!!3h7!!!!$=HbQW!!3h8!!!!%=J^wI!!3h:!!!!" +
                "#=J^wI!!3h@!!!!%=J^wI!!3hB!!!!$=HbQW!!3hC!!!!$=HbQW!!3hL!!!!$=HbQW!!3hQ!!!!" +
                "$=HbQW!!3hS!!!!%=J^wI!!3hU!!!!$=HbQW!!3h[!!!!$=HbQW!!3h^!!!!$=HbQW!!3hd!!!!" +
                "%=J^wI!!3he!!!!%=J^wI!!3hf!!!!%=J^wI!!3hg!!!!$=HbQW!!3hh!!!!%=J^wI!!3hi!!!!" +
                "%=J^wI!!3hv!!!!$=HbQW!!3i/!!!!#=J^wI!!3i2!!!!#=J^wI!!3i3!!!!%=J^wI!!3i4!!!!" +
                "$=HbQW!!3i7!!!!$=HbQW!!3i8!!!!$=HbQW!!3i9!!!!%=J^wI!!3i=!!!!#=J^wI!!3i>!!!!" +
                "%=J^wI!!3iD!!!!$=HbQW!!3iF!!!!#=J^wI!!3iH!!!!%=J^wI!!3iM!!!!%=J^wI!!3iS!!!!" +
                "#=J^wI!!3iU!!!!%=J^wI!!3iZ!!!!#=J^wI!!3i]!!!!%=J^wI!!3ig!!!!%=J^wI!!3ij!!!!" +
                "%=J^wI!!3ik!!!!#=J^wI!!3il!!!!$=HbQW!!3in!!!!%=J^wI!!3ip!!!!$=HbQW!!3iq!!!!" +
                "$=HbQW!!3it!!!!%=J^wI!!3ix!!!!#=J^wI!!3j!!!!!$=HbQW!!3j%!!!!$=HbQW!!3j'!!!!" +
                "%=J^wI!!3j(!!!!%=J^wI!!9mJ!!!!'=KqtH!!=SE!!<NC=MN(F!!?VS!!<NC=MN(F!!Zw`!!!!" +
                "%=KqtH!!j+C!!<NC=MN(F!!j+M!!<NC=MN(F!!j+a!!<NC=MN(F!!j,.!!<NC=MN(F!!n>M!!!!" +
                "'=KqtH!!s1X!!!!$=MMyc!!s1_!!!!#=MN#O!!ypn!!!!'=KqtH!!ypr!!!!'=KqtH!#%h!!!!!" +
                "%=KqtH!#%o!!!!!'=KqtH!#)H6!!<NC=MN(F!#*%'!!!!%=KqtH!#+k(!!!!'=KqtH!#-E!!!!!" +
                "'=KqtH!#1)w!!!!'=KqtH!#1)y!!!!'=KqtH!#1*M!!!!#=KqtH!#1*p!!!!'=KqtH!#14Q!!<N" +
                "C=MN(F!#14S!!<NC=MN(F!#16I!!<NC=MN(F!#16N!!<NC=MN(F!#16X!!<NC=MN(F!#16k!!<N" +
                "C=MN(F!#17@!!<NC=MN(F!#17A!!<NC=MN(F!#1Cq!!!!'=KqtH!#7),!!!!#=KqtH!#7)b!!!!" +
                "#=KqtH!#7Ww!!!!'=KqtH!#?cQ!!!!'=KqtH!#His!!!!'=KqtH!#Jrh!!!!'=KqtH!#O@M!!<N" +
                "C=MN(F!#O@O!!<NC=MN(F!#OC6!!<NC=MN(F!#Os.!!!!#=KqtH!#YOW!!!!#=H/Li!#Zat!!!!" +
                "'=KqtH!#ZbI!!!!%=KqtH!#Zbc!!!!'=KqtH!#Zbs!!!!%=KqtH!#Zby!!!!'=KqtH!#Zce!!!!" +
                "'=KqtH!#Zdc!!!!%=KqtH!#Zea!!!!'=KqtH!#ZhI!!!!#=KqtH!#ZiD!!!!'=KqtH!#Zis!!!!" +
                "'=KqtH!#Zj0!!!!#=KqtH!#Zj1!!!!'=KqtH!#Zj[!!!!'=KqtH!#Zj]!!!!'=KqtH!#Zj^!!!!" +
                "'=KqtH!#Zjb!!!!'=KqtH!#Zk!!!!!'=KqtH!#Zk6!!!!#=KqtH!#Zk9!!!!%=KqtH!#Zk<!!!!" +
                "'=KqtH!#Zl>!!!!'=KqtH!#]9R!!!!$=H/Lt!#]I6!!!!#=KqtH!#]Z#!!!!%=KqtH!#^*N!!!!" +
                "#=KqtH!#^:m!!!!#=KqtH!#_*_!!!!%=J^wI!#`-7!!!!#=KqtH!#`T>!!!!'=KqtH!#`T?!!!!" +
                "'=KqtH!#`TA!!!!'=KqtH!#`TB!!!!'=KqtH!#`TG!!!!'=KqtH!#`TP!!!!#=KqtH!#`U,!!!!" +
                "'=KqtH!#`U/!!!!'=KqtH!#`U0!!!!#=KqtH!#`U9!!!!'=KqtH!#aEQ!!!!%=KqtH!#b<)!!!!" +
                "'=KqtH!#c9-!!!!%=KqtH!#dxC!!!!%=KqtH!#dxE!!!!%=KqtH!#ev$!!!!'=KqtH!#fBi!!!!" +
                "#=KqtH!#fBj!!!!'=KqtH!#fG)!!!!'=KqtH!#fG+!!!!'=KqtH!#g<d!!!!'=KqtH!#g<e!!!!" +
                "'=KqtH!#g=J!!!!'=KqtH!#gat!!!!#=KqtH!#s`D!!!!#=J_#p!#sg?!!!!#=J_#p!#t<a!!!!" +
                "#=KqtH!#t<c!!!!#=KqtH!#trY!!!!$=JiYj!#vA$!!!!'=KqtH!#xs_!!!!'=KqtH!$$rO!!!!" +
                "#=KqtH!$$rP!!!!#=KqtH!$(!%!!!!'=KqtH!$)]o!!!!%=KqtH!$,@)!!!!'=KqtH!$,k]!!!!" +
                "'=KqtH!$1]+!!!!%=KqtH!$3IO!!!!%=KqtH!$3J#!!!!'=KqtH!$3J.!!!!'=KqtH!$3J:!!!!" +
                "#=KqtH!$3JH!!!!#=KqtH!$3JI!!!!#=KqtH!$3JK!!!!%=KqtH!$3JL!!!!'=KqtH!$3JS!!!!" +
                "'=KqtH!$8+M!!!!#=KqtH!$99d!!!!%=KqtH!$:Lw!!!!#=LK+x!$:N@!!!!#=KqtG!$:NC!!!!" +
                "#=KqtG!$:hW!!!!'=KqtH!$:i[!!!!'=KqtH!$:ih!!!!'=KqtH!$:it!!!!'=KqtH!$:kO!!!!" +
                "'=KqtH!$>*B!!!!'=KqtH!$>hD!!!!+=J^x0!$?lW!!!!'=KqtH!$?ll!!!!'=KqtH!$?lm!!!!" +
                "%=KqtH!$?mi!!!!'=KqtH!$?mx!!!!'=KqtH!$D7]!!!!#=J_#p!$D@T!!!!#=J_#p!$V<g!!!!" +
                "'=KqtH";

        Set<Cookie> cookies = CookieDecoder.decode("bh=\"" + longValue + "\";");
        assertEquals(1, cookies.size());
        Cookie c = cookies.iterator().next();
        assertEquals("bh", c.getName());
        assertEquals(longValue, c.getValue());
    }
}
