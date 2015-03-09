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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import org.junit.Test;

public class ClientCookieDecoderTest {
    @Test
    public void testDecodingSingleCookieV0() {
        String cookieString = "myCookie=myValue;expires=XXX;path=/apathsomewhere;domain=.adomainsomewhere;secure;";
        cookieString = cookieString.replace("XXX", HttpHeaderDateFormat.get()
                .format(new Date(System.currentTimeMillis() + 50000)));

        Cookie cookie = ClientCookieDecoder.decode(cookieString);
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());

        boolean fail = true;
        for (int i = 40; i <= 60; i++) {
            if (cookie.maxAge() == i) {
                fail = false;
                break;
            }
        }
        if (fail) {
            fail("expected: 50, actual: " + cookie.maxAge());
        }

        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingSingleCookieV0ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=0;" +
                "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        Cookie cookie = ClientCookieDecoder.decode(cookieString);
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingSingleCookieV1() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere"
                + ";secure;comment=this is a comment;version=1;";
        Cookie cookie = ClientCookieDecoder.decode(cookieString);
        assertEquals("myValue", cookie.value());
        assertNotNull(cookie);
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingSingleCookieV1ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=1;"
                + "commentURL=http://aurl.com;port='80,8080';discard;";
        Cookie cookie = ClientCookieDecoder.decode(cookieString);
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingSingleCookieV2() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                + "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        Cookie cookie = ClientCookieDecoder.decode(cookieString);
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingComplexCookie() {
        String c1 = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                + "commentURL=\"http://aurl.com\";port='80,8080';discard;";

        Cookie cookie = ClientCookieDecoder.decode(c1);
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void testDecodingQuotedCookie() {
        Collection<String> sources = new ArrayList<String>();
        sources.add("a=\"\",");
        sources.add("b=\"1\",");
        sources.add("c=\"\\\"1\\\"2\\\"\",");
        sources.add("d=\"1\\\"2\\\"3\",");
        sources.add("e=\"\\\"\\\"\",");
        sources.add("f=\"1\\\"\\\"2\",");
        sources.add("g=\"\\\\\",");
        sources.add("h=\"';,\\x\"");

        Collection<Cookie> cookies = new ArrayList<Cookie>();
        for (String source : sources) {
            cookies.add(ClientCookieDecoder.decode(source));
        }

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
        String source = "ARPT=LWUKQPSWRTUN04CKKJI; "
                + "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; "
                + "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; "
                + "__utmb=48461872.13.10.1258140131; __utmc=48461872; "
                + "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|"
                + "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html";
        Cookie cookie = ClientCookieDecoder.decode(source);

        assertEquals("ARPT", cookie.name());
        assertEquals("LWUKQPSWRTUN04CKKJI", cookie.value());
    }

    @Test
    public void testDecodingLongDates() {
        Calendar cookieDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cookieDate.set(9999, Calendar.DECEMBER, 31, 23, 59, 59);
        long expectedMaxAge = (cookieDate.getTimeInMillis() - System
                .currentTimeMillis()) / 1000;

        String source = "Format=EU; expires=Fri, 31-Dec-9999 23:59:59 GMT; path=/";

        Cookie cookie = ClientCookieDecoder.decode(source);

        assertTrue(Math.abs(expectedMaxAge - cookie.maxAge()) < 2);
    }

    @Test
    public void testDecodingValueWithComma() {
        String source = "UserCookie=timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE;"
                + " expires=Sat, 01-Dec-2012 10:53:31 GMT; path=/";

        Cookie cookie = ClientCookieDecoder.decode(source);

        assertEquals(
                "timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE",
                cookie.value());
    }

    @Test
    public void testDecodingWeirdNames1() {
        String src = "path=; expires=Mon, 01-Jan-1990 00:00:00 GMT; path=/; domain=.www.google.com";
        Cookie cookie = ClientCookieDecoder.decode(src);
        assertEquals("path", cookie.name());
        assertEquals("", cookie.value());
        assertEquals("/", cookie.path());
    }

    @Test
    public void testDecodingWeirdNames2() {
        String src = "HTTPOnly=";
        Cookie cookie = ClientCookieDecoder.decode(src);
        assertEquals("HTTPOnly", cookie.name());
        assertEquals("", cookie.value());
    }

    @Test
    public void testDecodingValuesWithCommasAndEquals() {
        String src = "A=v=1&lg=en-US,it-IT,it&intl=it&np=1;T=z=E";
        Cookie cookie = ClientCookieDecoder.decode(src);
        assertEquals("A", cookie.name());
        assertEquals("v=1&lg=en-US,it-IT,it&intl=it&np=1", cookie.value());
    }

    @Test
    public void testDecodingLongValue() {
        String longValue = "b!!!$Q!!$ha!!<NC=MN(F!!%#4!!<NC=MN(F!!2!d!!!!#=IvZB!!2,F!!!!'=KqtH!!2-9!!!!"
                + "'=IvZM!!3f:!!!!$=HbQW!!3g'!!!!%=J^wI!!3g-!!!!%=J^wI!!3g1!!!!$=HbQW!!3g2!!!!"
                + "$=HbQW!!3g5!!!!%=J^wI!!3g9!!!!$=HbQW!!3gT!!!!$=HbQW!!3gX!!!!#=J^wI!!3gY!!!!"
                + "#=J^wI!!3gh!!!!$=HbQW!!3gj!!!!$=HbQW!!3gr!!!!$=HbQW!!3gx!!!!#=J^wI!!3h!!!!!"
                + "$=HbQW!!3h$!!!!#=J^wI!!3h'!!!!$=HbQW!!3h,!!!!$=HbQW!!3h0!!!!%=J^wI!!3h1!!!!"
                + "#=J^wI!!3h2!!!!$=HbQW!!3h4!!!!$=HbQW!!3h7!!!!$=HbQW!!3h8!!!!%=J^wI!!3h:!!!!"
                + "#=J^wI!!3h@!!!!%=J^wI!!3hB!!!!$=HbQW!!3hC!!!!$=HbQW!!3hL!!!!$=HbQW!!3hQ!!!!"
                + "$=HbQW!!3hS!!!!%=J^wI!!3hU!!!!$=HbQW!!3h[!!!!$=HbQW!!3h^!!!!$=HbQW!!3hd!!!!"
                + "%=J^wI!!3he!!!!%=J^wI!!3hf!!!!%=J^wI!!3hg!!!!$=HbQW!!3hh!!!!%=J^wI!!3hi!!!!"
                + "%=J^wI!!3hv!!!!$=HbQW!!3i/!!!!#=J^wI!!3i2!!!!#=J^wI!!3i3!!!!%=J^wI!!3i4!!!!"
                + "$=HbQW!!3i7!!!!$=HbQW!!3i8!!!!$=HbQW!!3i9!!!!%=J^wI!!3i=!!!!#=J^wI!!3i>!!!!"
                + "%=J^wI!!3iD!!!!$=HbQW!!3iF!!!!#=J^wI!!3iH!!!!%=J^wI!!3iM!!!!%=J^wI!!3iS!!!!"
                + "#=J^wI!!3iU!!!!%=J^wI!!3iZ!!!!#=J^wI!!3i]!!!!%=J^wI!!3ig!!!!%=J^wI!!3ij!!!!"
                + "%=J^wI!!3ik!!!!#=J^wI!!3il!!!!$=HbQW!!3in!!!!%=J^wI!!3ip!!!!$=HbQW!!3iq!!!!"
                + "$=HbQW!!3it!!!!%=J^wI!!3ix!!!!#=J^wI!!3j!!!!!$=HbQW!!3j%!!!!$=HbQW!!3j'!!!!"
                + "%=J^wI!!3j(!!!!%=J^wI!!9mJ!!!!'=KqtH!!=SE!!<NC=MN(F!!?VS!!<NC=MN(F!!Zw`!!!!"
                + "%=KqtH!!j+C!!<NC=MN(F!!j+M!!<NC=MN(F!!j+a!!<NC=MN(F!!j,.!!<NC=MN(F!!n>M!!!!"
                + "'=KqtH!!s1X!!!!$=MMyc!!s1_!!!!#=MN#O!!ypn!!!!'=KqtH!!ypr!!!!'=KqtH!#%h!!!!!"
                + "%=KqtH!#%o!!!!!'=KqtH!#)H6!!<NC=MN(F!#*%'!!!!%=KqtH!#+k(!!!!'=KqtH!#-E!!!!!"
                + "'=KqtH!#1)w!!!!'=KqtH!#1)y!!!!'=KqtH!#1*M!!!!#=KqtH!#1*p!!!!'=KqtH!#14Q!!<N"
                + "C=MN(F!#14S!!<NC=MN(F!#16I!!<NC=MN(F!#16N!!<NC=MN(F!#16X!!<NC=MN(F!#16k!!<N"
                + "C=MN(F!#17@!!<NC=MN(F!#17A!!<NC=MN(F!#1Cq!!!!'=KqtH!#7),!!!!#=KqtH!#7)b!!!!"
                + "#=KqtH!#7Ww!!!!'=KqtH!#?cQ!!!!'=KqtH!#His!!!!'=KqtH!#Jrh!!!!'=KqtH!#O@M!!<N"
                + "C=MN(F!#O@O!!<NC=MN(F!#OC6!!<NC=MN(F!#Os.!!!!#=KqtH!#YOW!!!!#=H/Li!#Zat!!!!"
                + "'=KqtH!#ZbI!!!!%=KqtH!#Zbc!!!!'=KqtH!#Zbs!!!!%=KqtH!#Zby!!!!'=KqtH!#Zce!!!!"
                + "'=KqtH!#Zdc!!!!%=KqtH!#Zea!!!!'=KqtH!#ZhI!!!!#=KqtH!#ZiD!!!!'=KqtH!#Zis!!!!"
                + "'=KqtH!#Zj0!!!!#=KqtH!#Zj1!!!!'=KqtH!#Zj[!!!!'=KqtH!#Zj]!!!!'=KqtH!#Zj^!!!!"
                + "'=KqtH!#Zjb!!!!'=KqtH!#Zk!!!!!'=KqtH!#Zk6!!!!#=KqtH!#Zk9!!!!%=KqtH!#Zk<!!!!"
                + "'=KqtH!#Zl>!!!!'=KqtH!#]9R!!!!$=H/Lt!#]I6!!!!#=KqtH!#]Z#!!!!%=KqtH!#^*N!!!!"
                + "#=KqtH!#^:m!!!!#=KqtH!#_*_!!!!%=J^wI!#`-7!!!!#=KqtH!#`T>!!!!'=KqtH!#`T?!!!!"
                + "'=KqtH!#`TA!!!!'=KqtH!#`TB!!!!'=KqtH!#`TG!!!!'=KqtH!#`TP!!!!#=KqtH!#`U,!!!!"
                + "'=KqtH!#`U/!!!!'=KqtH!#`U0!!!!#=KqtH!#`U9!!!!'=KqtH!#aEQ!!!!%=KqtH!#b<)!!!!"
                + "'=KqtH!#c9-!!!!%=KqtH!#dxC!!!!%=KqtH!#dxE!!!!%=KqtH!#ev$!!!!'=KqtH!#fBi!!!!"
                + "#=KqtH!#fBj!!!!'=KqtH!#fG)!!!!'=KqtH!#fG+!!!!'=KqtH!#g<d!!!!'=KqtH!#g<e!!!!"
                + "'=KqtH!#g=J!!!!'=KqtH!#gat!!!!#=KqtH!#s`D!!!!#=J_#p!#sg?!!!!#=J_#p!#t<a!!!!"
                + "#=KqtH!#t<c!!!!#=KqtH!#trY!!!!$=JiYj!#vA$!!!!'=KqtH!#xs_!!!!'=KqtH!$$rO!!!!"
                + "#=KqtH!$$rP!!!!#=KqtH!$(!%!!!!'=KqtH!$)]o!!!!%=KqtH!$,@)!!!!'=KqtH!$,k]!!!!"
                + "'=KqtH!$1]+!!!!%=KqtH!$3IO!!!!%=KqtH!$3J#!!!!'=KqtH!$3J.!!!!'=KqtH!$3J:!!!!"
                + "#=KqtH!$3JH!!!!#=KqtH!$3JI!!!!#=KqtH!$3JK!!!!%=KqtH!$3JL!!!!'=KqtH!$3JS!!!!"
                + "'=KqtH!$8+M!!!!#=KqtH!$99d!!!!%=KqtH!$:Lw!!!!#=LK+x!$:N@!!!!#=KqtG!$:NC!!!!"
                + "#=KqtG!$:hW!!!!'=KqtH!$:i[!!!!'=KqtH!$:ih!!!!'=KqtH!$:it!!!!'=KqtH!$:kO!!!!"
                + "'=KqtH!$>*B!!!!'=KqtH!$>hD!!!!+=J^x0!$?lW!!!!'=KqtH!$?ll!!!!'=KqtH!$?lm!!!!"
                + "%=KqtH!$?mi!!!!'=KqtH!$?mx!!!!'=KqtH!$D7]!!!!#=J_#p!$D@T!!!!#=J_#p!$V<g!!!!"
                + "'=KqtH";

        Cookie cookie = ClientCookieDecoder.decode("bh=\"" + longValue
                                                   + "\";");
        assertEquals("bh", cookie.name());
        assertEquals(longValue, cookie.value());
    }

    @Test
    public void testIgnoreEmptyDomain() {
        String emptyDomain = "sessionid=OTY4ZDllNTgtYjU3OC00MWRjLTkzMWMtNGUwNzk4MTY0MTUw;Domain=;Path=/";
        Cookie cookie = ClientCookieDecoder.decode(emptyDomain);
        assertNull(cookie.domain());
    }
}
