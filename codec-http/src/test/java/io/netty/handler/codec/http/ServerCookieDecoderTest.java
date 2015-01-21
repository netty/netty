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

import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;

public class ServerCookieDecoderTest {
    @Test
    public void testDecodingSingleCookie() {
        String cookieString = "myCookie=myValue";
        cookieString = cookieString.replace("XXX",
                HttpHeaderDateFormat.get().format(new Date(System.currentTimeMillis() + 50000)));

        Set<Cookie> cookies = ServerCookieDecoder.decode(cookieString);
        assertEquals(1, cookies.size());
        Cookie cookie = cookies.iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
    }

    @Test
    public void testDecodingMultipleCookies() {
        String c1 = "myCookie=myValue;";
        String c2 = "myCookie2=myValue2;";
        String c3 = "myCookie3=myValue3;";

        Set<Cookie> cookies = ServerCookieDecoder.decode(c1 + c2 + c3);
        assertEquals(3, cookies.size());
        Iterator<Cookie> it = cookies.iterator();
        Cookie cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue2", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue3", cookie.value());
    }

    @Test
    public void testDecodingQuotedCookie() {
        String source =
            "a=\"\";" +
            "b=\"1\";" +
            "c=\"\\\"1\\\"2\\\"\";" +
            "d=\"1\\\"2\\\"3\";" +
            "e=\"\\\"\\\"\";" +
            "f=\"1\\\"\\\"2\";" +
            "g=\"\\\\\";" +
            "h=\"';,\\x\"";

        Set<Cookie> cookies = ServerCookieDecoder.decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals("a", c.name());
        assertEquals("", c.value());

        c = it.next();
        assertEquals("b", c.name());
        assertEquals("1", c.value());

        c = it.next();
        assertEquals("c", c.name());
        assertEquals("\"1\"2\"", c.value());

        c = it.next();
        assertEquals("d", c.name());
        assertEquals("1\"2\"3", c.value());

        c = it.next();
        assertEquals("e", c.name());
        assertEquals("\"\"", c.value());

        c = it.next();
        assertEquals("f", c.name());
        assertEquals("1\"\"2", c.value());

        c = it.next();
        assertEquals("g", c.name());
        assertEquals("\\", c.value());

        c = it.next();
        assertEquals("h", c.name());
        assertEquals("';,\\x", c.value());

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
        Set<Cookie> cookies = ServerCookieDecoder.decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals("__utma", c.name());
        assertEquals("48461872.1094088325.1258140131.1258140131.1258140131.1", c.value());

        c = it.next();
        assertEquals("__utmb", c.name());
        assertEquals("48461872.13.10.1258140131", c.value());

        c = it.next();
        assertEquals("__utmc", c.name());
        assertEquals("48461872", c.value());

        c = it.next();
        assertEquals("__utmz", c.name());
        assertEquals("48461872.1258140131.1.1.utmcsr=overstock.com|" +
                "utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html",
                c.value());

        c = it.next();
        assertEquals("ARPT", c.name());
        assertEquals("LWUKQPSWRTUN04CKKJI", c.value());

        c = it.next();
        assertEquals("kw-2E343B92-B097-442c-BFA5-BE371E0325A2", c.name());
        assertEquals("unfinished furniture", c.value());

        assertFalse(it.hasNext());
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

        Set<Cookie> cookies = ServerCookieDecoder.decode("bh=\"" + longValue + "\";");
        assertEquals(1, cookies.size());
        Cookie c = cookies.iterator().next();
        assertEquals("bh", c.name());
        assertEquals(longValue, c.value());
    }

    @Test
    public void testDecodingOldRFC2965Cookies() {
        String source = "$Version=\"1\"; " +
                "Part_Number1=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; " +
                "Part_Number2=\"Rocket_Launcher_0001\"; $Path=\"/acme\"";

        Set<Cookie> cookies = ServerCookieDecoder.decode(source);
        Iterator<Cookie> it = cookies.iterator();
        Cookie c;

        c = it.next();
        assertEquals("Part_Number1", c.name());
        assertEquals("Riding_Rocket_0023", c.value());

        c = it.next();
        assertEquals("Part_Number2", c.name());
        assertEquals("Rocket_Launcher_0001", c.value());

        assertFalse(it.hasNext());
    }
}
