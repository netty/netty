/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.handler.codec.DateFormatter;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static io.netty5.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty5.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static io.netty5.handler.codec.http.headers.HttpSetCookie.SameSite.None;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests from the old Netty 4.1 ClientCookieDecoder and ServerCookieDecoder.
 * Those decoders were tested on cookie formats for older RFCs.
 * We recreate those tests here, to verify that we're still compatible with those formats.
 */
public class LegacyCookieParsingTest {
    HttpHeaders headers;

    @BeforeEach
    void createHeaders() {
        headers = HttpHeaders.newHeaders(false);
    }

    // Server cookie (COOKIE header) decoder tests
    @Test
    void decodingSingleServerCookie() {
        headers.add(COOKIE, "myCookie=myValue");
        assertThat(headers.getCookies()).hasSize(1);
        assertThat(headers.getCookies()).containsExactly(new DefaultHttpCookiePair("myCookie", "myValue"));
    }

    @Test
    void decodingMultipleServerCookies() {
        String c1 = "myCookie=myValue;";
        String c2 = "myCookie2=myValue2;";
        String c3 = "myCookie3=myValue3;";
        headers.add(COOKIE, c1 + c2 + c3);
        assertThat(headers.getCookies()).hasSize(3);
        assertThat(headers.getCookies()).containsExactly(
                new DefaultHttpCookiePair("myCookie", "myValue"),
                new DefaultHttpCookiePair("myCookie2", "myValue2"),
                new DefaultHttpCookiePair("myCookie3", "myValue3"));
    }

    @Test
    void decodingMultipleSameNamedServerCookies() {
        String c1 = "myCookie=myValue;";
        String c2 = "myCookie=myValue2;";
        String c3 = "myCookie=myValue3;";
        headers.add(COOKIE, c1 + c2 + c3);
        assertThat(headers.getCookies()).hasSize(3);
        assertThat(headers.getCookies()).containsExactly(
                new DefaultHttpCookiePair("myCookie", "myValue"),
                new DefaultHttpCookiePair("myCookie", "myValue2"),
                new DefaultHttpCookiePair("myCookie", "myValue3"));
    }

    @Test
    void decodingGoogleAnalyticsServerCookie() {
        String source = "ARPT=LWUKQPSWRTUN04CKKJI; " +
                "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished_furniture; " +
                "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; " +
                "__utmb=48461872.13.10.1258140131; __utmc=48461872; " +
                "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|" +
                "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance/clearance/32/dept.html";
        Iterable<HttpCookiePair> cookies = headers.add(COOKIE, source).getCookies();
        assertThat(cookies).containsExactly(
                new DefaultHttpCookiePair("ARPT", "LWUKQPSWRTUN04CKKJI"),
                new DefaultHttpCookiePair("kw-2E343B92-B097-442c-BFA5-BE371E0325A2", "unfinished_furniture"),
                new DefaultHttpCookiePair("__utma", "48461872.1094088325.1258140131.1258140131.1258140131.1"),
                new DefaultHttpCookiePair("__utmb", "48461872.13.10.1258140131"),
                new DefaultHttpCookiePair("__utmc", "48461872"),
                new DefaultHttpCookiePair("__utmz", "48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|" +
                        "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance/clearance/32/dept.html"));
    }

    @Test
    void decodingLongServerCookieValue() {
        String longValue =
                "b___$Q__$ha__<NC=MN(F__%#4__<NC=MN(F__2_d____#=IvZB__2_F____'=KqtH__2-9____" +
                        "'=IvZM__3f:____$=HbQW__3g'____%=J^wI__3g-____%=J^wI__3g1____$=HbQW__3g2____" +
                        "$=HbQW__3g5____%=J^wI__3g9____$=HbQW__3gT____$=HbQW__3gX____#=J^wI__3gY____" +
                        "#=J^wI__3gh____$=HbQW__3gj____$=HbQW__3gr____$=HbQW__3gx____#=J^wI__3h_____" +
                        "$=HbQW__3h$____#=J^wI__3h'____$=HbQW__3h_____$=HbQW__3h0____%=J^wI__3h1____" +
                        "#=J^wI__3h2____$=HbQW__3h4____$=HbQW__3h7____$=HbQW__3h8____%=J^wI__3h:____" +
                        "#=J^wI__3h@____%=J^wI__3hB____$=HbQW__3hC____$=HbQW__3hL____$=HbQW__3hQ____" +
                        "$=HbQW__3hS____%=J^wI__3hU____$=HbQW__3h[____$=HbQW__3h^____$=HbQW__3hd____" +
                        "%=J^wI__3he____%=J^wI__3hf____%=J^wI__3hg____$=HbQW__3hh____%=J^wI__3hi____" +
                        "%=J^wI__3hv____$=HbQW__3i/____#=J^wI__3i2____#=J^wI__3i3____%=J^wI__3i4____" +
                        "$=HbQW__3i7____$=HbQW__3i8____$=HbQW__3i9____%=J^wI__3i=____#=J^wI__3i>____" +
                        "%=J^wI__3iD____$=HbQW__3iF____#=J^wI__3iH____%=J^wI__3iM____%=J^wI__3iS____" +
                        "#=J^wI__3iU____%=J^wI__3iZ____#=J^wI__3i]____%=J^wI__3ig____%=J^wI__3ij____" +
                        "%=J^wI__3ik____#=J^wI__3il____$=HbQW__3in____%=J^wI__3ip____$=HbQW__3iq____" +
                        "$=HbQW__3it____%=J^wI__3ix____#=J^wI__3j_____$=HbQW__3j%____$=HbQW__3j'____" +
                        "%=J^wI__3j(____%=J^wI__9mJ____'=KqtH__=SE__<NC=MN(F__?VS__<NC=MN(F__Zw`____" +
                        "%=KqtH__j+C__<NC=MN(F__j+M__<NC=MN(F__j+a__<NC=MN(F__j_.__<NC=MN(F__n>M____" +
                        "'=KqtH__s1X____$=MMyc__s1_____#=MN#O__ypn____'=KqtH__ypr____'=KqtH_#%h_____" +
                        "%=KqtH_#%o_____'=KqtH_#)H6__<NC=MN(F_#*%'____%=KqtH_#+k(____'=KqtH_#-E_____" +
                        "'=KqtH_#1)w____'=KqtH_#1)y____'=KqtH_#1*M____#=KqtH_#1*p____'=KqtH_#14Q__<N" +
                        "C=MN(F_#14S__<NC=MN(F_#16I__<NC=MN(F_#16N__<NC=MN(F_#16X__<NC=MN(F_#16k__<N" +
                        "C=MN(F_#17@__<NC=MN(F_#17A__<NC=MN(F_#1Cq____'=KqtH_#7)_____#=KqtH_#7)b____" +
                        "#=KqtH_#7Ww____'=KqtH_#?cQ____'=KqtH_#His____'=KqtH_#Jrh____'=KqtH_#O@M__<N" +
                        "C=MN(F_#O@O__<NC=MN(F_#OC6__<NC=MN(F_#Os.____#=KqtH_#YOW____#=H/Li_#Zat____" +
                        "'=KqtH_#ZbI____%=KqtH_#Zbc____'=KqtH_#Zbs____%=KqtH_#Zby____'=KqtH_#Zce____" +
                        "'=KqtH_#Zdc____%=KqtH_#Zea____'=KqtH_#ZhI____#=KqtH_#ZiD____'=KqtH_#Zis____" +
                        "'=KqtH_#Zj0____#=KqtH_#Zj1____'=KqtH_#Zj[____'=KqtH_#Zj]____'=KqtH_#Zj^____" +
                        "'=KqtH_#Zjb____'=KqtH_#Zk_____'=KqtH_#Zk6____#=KqtH_#Zk9____%=KqtH_#Zk<____" +
                        "'=KqtH_#Zl>____'=KqtH_#]9R____$=H/Lt_#]I6____#=KqtH_#]Z#____%=KqtH_#^*N____" +
                        "#=KqtH_#^:m____#=KqtH_#_*_____%=J^wI_#`-7____#=KqtH_#`T>____'=KqtH_#`T?____" +
                        "'=KqtH_#`TA____'=KqtH_#`TB____'=KqtH_#`TG____'=KqtH_#`TP____#=KqtH_#`U_____" +
                        "'=KqtH_#`U/____'=KqtH_#`U0____#=KqtH_#`U9____'=KqtH_#aEQ____%=KqtH_#b<)____" +
                        "'=KqtH_#c9-____%=KqtH_#dxC____%=KqtH_#dxE____%=KqtH_#ev$____'=KqtH_#fBi____" +
                        "#=KqtH_#fBj____'=KqtH_#fG)____'=KqtH_#fG+____'=KqtH_#g<d____'=KqtH_#g<e____" +
                        "'=KqtH_#g=J____'=KqtH_#gat____#=KqtH_#s`D____#=J_#p_#sg?____#=J_#p_#t<a____" +
                        "#=KqtH_#t<c____#=KqtH_#trY____$=JiYj_#vA$____'=KqtH_#xs_____'=KqtH_$$rO____" +
                        "#=KqtH_$$rP____#=KqtH_$(_%____'=KqtH_$)]o____%=KqtH_$_@)____'=KqtH_$_k]____" +
                        "'=KqtH_$1]+____%=KqtH_$3IO____%=KqtH_$3J#____'=KqtH_$3J.____'=KqtH_$3J:____" +
                        "#=KqtH_$3JH____#=KqtH_$3JI____#=KqtH_$3JK____%=KqtH_$3JL____'=KqtH_$3JS____" +
                        "'=KqtH_$8+M____#=KqtH_$99d____%=KqtH_$:Lw____#=LK+x_$:N@____#=KqtG_$:NC____" +
                        "#=KqtG_$:hW____'=KqtH_$:i[____'=KqtH_$:ih____'=KqtH_$:it____'=KqtH_$:kO____" +
                        "'=KqtH_$>*B____'=KqtH_$>hD____+=J^x0_$?lW____'=KqtH_$?ll____'=KqtH_$?lm____" +
                        "%=KqtH_$?mi____'=KqtH_$?mx____'=KqtH_$D7]____#=J_#p_$D@T____#=J_#p_$V<g____" +
                        "'=KqtH";
        Iterable<HttpCookiePair> cookies = headers.add(COOKIE, "bh=\"" + longValue + "\";").getCookies();
        assertThat(cookies).hasSize(1);
        assertThat(cookies).containsExactly(new DefaultHttpCookiePair("bh", longValue));
    }

    /**
     * Note: here we're deviating from Netty 4.1. We don't support RFC 2965 cookies anymore.
     */
    @Test
    void decodingOldRFC2965Cookies() {
        String source = "$Version=\"1\"; " +
                "Part_Number1=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; " +
                "Part_Number2=\"Rocket_Launcher_0001\"; $Path=\"/acme\"";

        Iterable<HttpCookiePair> cookies = headers.add(COOKIE, source).getCookies();
        assertThat(cookies).containsExactly(
                new DefaultHttpCookiePair("$Version", "1"), // Would be hidden in RFC 2965
                new DefaultHttpCookiePair("Part_Number1", "Riding_Rocket_0023"),
                new DefaultHttpCookiePair("$Path", "/acme/ammo"), // Would be hidden in RFC 2965
                new DefaultHttpCookiePair("Part_Number2", "Rocket_Launcher_0001"),
                new DefaultHttpCookiePair("$Path", "/acme")); // Would be hidden in RFC 2965
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void rejectServerCookieWithSemicolonInValue() {
        headers.add(COOKIE, "name=\"foo;bar\";");
        var e = assertThrows(IllegalArgumentException.class, () -> headers.getCookies().iterator().hasNext());
        assertThat(e).hasMessageContaining("The ; character cannot appear in quoted cookie values");

        e = assertThrows(IllegalArgumentException.class, () -> headers.getCookies().iterator().next());
        assertThat(e).hasMessageContaining("The ; character cannot appear in quoted cookie values");
    }

    @Test
    void caseSensitiveServerCookieNames() {
        Iterable<HttpCookiePair> cookies = headers.add(COOKIE, "session_id=a; Session_id=b;").getCookies();
        assertThat(cookies).containsOnly(
                new DefaultHttpCookiePair("Session_id", "b"),
                new DefaultHttpCookiePair("session_id", "a"));
    }

    // Client cookie (SET_COOKIE header) decoder tests
    @Test
    public void testDecodingSingleCookieV0() {
        String cookieString = "myCookie=myValue;expires="
                + DateFormatter.format(new Date(System.currentTimeMillis() + 50000))
                + ";path=/apathsomewhere;domain=.adomainsomewhere;secure;SameSite=None";

        HttpSetCookie cookie = headers.add(SET_COOKIE, cookieString).getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertNotEquals(Long.MIN_VALUE, cookie.maxAge(),
                "maxAge should be defined when parsing cookie " + cookieString);
        assertNull(cookie.maxAge());
        assertNotNull(cookie.expiresAsMaxAge());
        assertTrue(cookie.expiresAsMaxAge() >= 40 && cookie.expiresAsMaxAge() <= 60,
                "maxAge should be about 50ms when parsing cookie " + cookieString);
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());

        MatcherAssert.assertThat(cookie, is(instanceOf(DefaultHttpSetCookie.class)));
        assertEquals(None, cookie.sameSite());
    }

    @Test
    public void testDecodingSingleCookieV0ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                "domain=.adomainsomewhere;secure;comment=this is a comment;version=0;" +
                "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        assertThat(headers.add(SET_COOKIE, cookieString).getSetCookies()).containsExactly(new DefaultHttpSetCookie(
                "myCookie", "myValue", "/apathsomewhere", ".adomainsomewhere", null, 50L,
                None, false, true, false));
    }

    @Test
    public void testDecodingSingleCookieV1() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere"
                + ";secure;comment=this is a comment;version=1;";
        assertThat(headers.add(SET_COOKIE, cookieString).getSetCookies()).containsExactly(new DefaultHttpSetCookie(
                "myCookie", "myValue", "/apathsomewhere", ".adomainsomewhere", null, 50L, None, false, true, false));
    }

    @Test
    public void testDecodingSingleCookieV1ExtraParamsIgnored() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=1;"
                + "commentURL=http://aurl.com;port='80,8080';discard;";
        assertThat(headers.add(SET_COOKIE, cookieString).getSetCookies()).containsExactly(new DefaultHttpSetCookie(
                "myCookie", "myValue", "/apathsomewhere", ".adomainsomewhere", null, 50L, None, false, true, false));
    }

    @Test
    public void testDecodingSingleCookieV2() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                + "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        assertThat(headers.add(SET_COOKIE, cookieString).getSetCookies()).containsExactly(new DefaultHttpSetCookie(
                "myCookie", "myValue", "/apathsomewhere", ".adomainsomewhere", null, 50L, None, false, true, false));
    }

    @Test
    public void testDecodingComplexCookie() {
        String cookieString = "myCookie=myValue;max-age=50;path=/apathsomewhere;"
                + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                + "commentURL=\"http://aurl.com\";port='80,8080';discard;";
        assertThat(headers.add(SET_COOKIE, cookieString).getSetCookies()).containsExactly(new DefaultHttpSetCookie(
                "myCookie", "myValue", "/apathsomewhere", ".adomainsomewhere", null, 50L, None, false, true, false));
    }

    /**
     * Note: deviating from Netty 4.1 test, where the strings had a trailing comma ','.
     * Use of commas as delimiters is an RFC 2965 syntax, but it never allowed trailing commas
     */
    @Test
    public void testDecodingQuotedCookie() {
        HttpSetCookie cookie = headers.add(SET_COOKIE, "a=\"\"").getSetCookie("a");
        assertEquals("a", cookie.name());
        assertEquals("", cookie.value());

        cookie = headers.add(SET_COOKIE, "b=\"1\"").getSetCookie("b");
        assertEquals("b", cookie.name());
        assertEquals("1", cookie.value());
    }

    @Test
    public void testDecodingGoogleAnalyticsCookie() {
        String source = "ARPT=LWUKQPSWRTUN04CKKJI; "
                + "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; "
                + "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; "
                + "__utmb=48461872.13.10.1258140131; __utmc=48461872; "
                + "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|"
                + "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html";
        headers.add(SET_COOKIE, source);
        HttpSetCookie cookie = headers.getSetCookie("ARPT");

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

        headers.add(SET_COOKIE, source);
        HttpSetCookie cookie = headers.getSetCookie("Format");

        assertTrue(Math.abs(expectedMaxAge - cookie.expiresAsMaxAge()) < 2);
    }

    /**
     * Deviating from the old cookie parser. Since obsoleting RFC 2965, commas are no longer a field delimiter,
     * and that means they are allowed in cookie values. Previously, parsing such a cookie would throw an exception.
     */
    @Test
    public void testDecodingValueWithCommaDoesNotFail() {
        String source = "UserCookie=timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE;"
                + " expires=Sat, 01-Dec-2012 10:53:31 GMT; path=/";

        headers.add(SET_COOKIE, source);
        HttpSetCookie cookie = headers.getSetCookie("UserCookie");
        assertThat(cookie).isEqualTo(new DefaultHttpSetCookie(
                "UserCookie", "timeZoneName=(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE",
                "/", null, "Sat, 01-Dec-2012 10:53:31 GMT", null, None, false, false, false
        ));
    }

    @Test
    public void testDecodingWeirdNames1() {
        String source = "path=; expires=Mon, 01-Jan-1990 00:00:00 GMT; path=/; domain=.www.google.com";
        headers.add(SET_COOKIE, source);
        HttpSetCookie cookie = headers.getSetCookie("path");
        assertEquals("path", cookie.name());
        assertEquals("", cookie.value());
        assertEquals("/", cookie.path());
    }

    @Test
    public void testDecodingWeirdNames2() {
        String source = "HTTPOnly=";
        headers.add(SET_COOKIE, source);
        HttpSetCookie cookie = headers.getSetCookie("HTTPOnly");
        assertEquals("HTTPOnly", cookie.name());
        assertEquals("", cookie.value());
    }

    @Test
    public void testDecodingValuesWithCommasAndEqualsFails() {
        headers = HttpHeaders.newHeaders(); // Enable validation
        String source = "A=v=1&lg=en-US,it-IT,it&intl=it&np=1;T=z=E";
        headers.add(SET_COOKIE, source);
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("A"));
    }

    @Test
    public void testDecodingInvalidValuesWithCommaAtStart() {
        headers = HttpHeaders.newHeaders(); // Enable validation
        headers.add(SET_COOKIE, ",");
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookies().iterator().next());
        headers.clear();
        headers.add(SET_COOKIE, ",a");
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookies().iterator().next());
        headers.clear();
        headers.add(SET_COOKIE, ",a=a");
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookies().iterator().next());
    }

    @Test
    public void testDecodingLongValue() {
        String longValue =
                "b___$Q__$ha__<NC=MN(F__%#4__<NC=MN(F__2_d____#=IvZB__2_F____'=KqtH__2-9____" +
                        "'=IvZM__3f:____$=HbQW__3g'____%=J^wI__3g-____%=J^wI__3g1____$=HbQW__3g2____" +
                        "$=HbQW__3g5____%=J^wI__3g9____$=HbQW__3gT____$=HbQW__3gX____#=J^wI__3gY____" +
                        "#=J^wI__3gh____$=HbQW__3gj____$=HbQW__3gr____$=HbQW__3gx____#=J^wI__3h_____" +
                        "$=HbQW__3h$____#=J^wI__3h'____$=HbQW__3h_____$=HbQW__3h0____%=J^wI__3h1____" +
                        "#=J^wI__3h2____$=HbQW__3h4____$=HbQW__3h7____$=HbQW__3h8____%=J^wI__3h:____" +
                        "#=J^wI__3h@____%=J^wI__3hB____$=HbQW__3hC____$=HbQW__3hL____$=HbQW__3hQ____" +
                        "$=HbQW__3hS____%=J^wI__3hU____$=HbQW__3h[____$=HbQW__3h^____$=HbQW__3hd____" +
                        "%=J^wI__3he____%=J^wI__3hf____%=J^wI__3hg____$=HbQW__3hh____%=J^wI__3hi____" +
                        "%=J^wI__3hv____$=HbQW__3i/____#=J^wI__3i2____#=J^wI__3i3____%=J^wI__3i4____" +
                        "$=HbQW__3i7____$=HbQW__3i8____$=HbQW__3i9____%=J^wI__3i=____#=J^wI__3i>____" +
                        "%=J^wI__3iD____$=HbQW__3iF____#=J^wI__3iH____%=J^wI__3iM____%=J^wI__3iS____" +
                        "#=J^wI__3iU____%=J^wI__3iZ____#=J^wI__3i]____%=J^wI__3ig____%=J^wI__3ij____" +
                        "%=J^wI__3ik____#=J^wI__3il____$=HbQW__3in____%=J^wI__3ip____$=HbQW__3iq____" +
                        "$=HbQW__3it____%=J^wI__3ix____#=J^wI__3j_____$=HbQW__3j%____$=HbQW__3j'____" +
                        "%=J^wI__3j(____%=J^wI__9mJ____'=KqtH__=SE__<NC=MN(F__?VS__<NC=MN(F__Zw`____" +
                        "%=KqtH__j+C__<NC=MN(F__j+M__<NC=MN(F__j+a__<NC=MN(F__j_.__<NC=MN(F__n>M____" +
                        "'=KqtH__s1X____$=MMyc__s1_____#=MN#O__ypn____'=KqtH__ypr____'=KqtH_#%h_____" +
                        "%=KqtH_#%o_____'=KqtH_#)H6__<NC=MN(F_#*%'____%=KqtH_#+k(____'=KqtH_#-E_____" +
                        "'=KqtH_#1)w____'=KqtH_#1)y____'=KqtH_#1*M____#=KqtH_#1*p____'=KqtH_#14Q__<N" +
                        "C=MN(F_#14S__<NC=MN(F_#16I__<NC=MN(F_#16N__<NC=MN(F_#16X__<NC=MN(F_#16k__<N" +
                        "C=MN(F_#17@__<NC=MN(F_#17A__<NC=MN(F_#1Cq____'=KqtH_#7)_____#=KqtH_#7)b____" +
                        "#=KqtH_#7Ww____'=KqtH_#?cQ____'=KqtH_#His____'=KqtH_#Jrh____'=KqtH_#O@M__<N" +
                        "C=MN(F_#O@O__<NC=MN(F_#OC6__<NC=MN(F_#Os.____#=KqtH_#YOW____#=H/Li_#Zat____" +
                        "'=KqtH_#ZbI____%=KqtH_#Zbc____'=KqtH_#Zbs____%=KqtH_#Zby____'=KqtH_#Zce____" +
                        "'=KqtH_#Zdc____%=KqtH_#Zea____'=KqtH_#ZhI____#=KqtH_#ZiD____'=KqtH_#Zis____" +
                        "'=KqtH_#Zj0____#=KqtH_#Zj1____'=KqtH_#Zj[____'=KqtH_#Zj]____'=KqtH_#Zj^____" +
                        "'=KqtH_#Zjb____'=KqtH_#Zk_____'=KqtH_#Zk6____#=KqtH_#Zk9____%=KqtH_#Zk<____" +
                        "'=KqtH_#Zl>____'=KqtH_#]9R____$=H/Lt_#]I6____#=KqtH_#]Z#____%=KqtH_#^*N____" +
                        "#=KqtH_#^:m____#=KqtH_#_*_____%=J^wI_#`-7____#=KqtH_#`T>____'=KqtH_#`T?____" +
                        "'=KqtH_#`TA____'=KqtH_#`TB____'=KqtH_#`TG____'=KqtH_#`TP____#=KqtH_#`U_____" +
                        "'=KqtH_#`U/____'=KqtH_#`U0____#=KqtH_#`U9____'=KqtH_#aEQ____%=KqtH_#b<)____" +
                        "'=KqtH_#c9-____%=KqtH_#dxC____%=KqtH_#dxE____%=KqtH_#ev$____'=KqtH_#fBi____" +
                        "#=KqtH_#fBj____'=KqtH_#fG)____'=KqtH_#fG+____'=KqtH_#g<d____'=KqtH_#g<e____" +
                        "'=KqtH_#g=J____'=KqtH_#gat____#=KqtH_#s`D____#=J_#p_#sg?____#=J_#p_#t<a____" +
                        "#=KqtH_#t<c____#=KqtH_#trY____$=JiYj_#vA$____'=KqtH_#xs_____'=KqtH_$$rO____" +
                        "#=KqtH_$$rP____#=KqtH_$(_%____'=KqtH_$)]o____%=KqtH_$_@)____'=KqtH_$_k]____" +
                        "'=KqtH_$1]+____%=KqtH_$3IO____%=KqtH_$3J#____'=KqtH_$3J.____'=KqtH_$3J:____" +
                        "#=KqtH_$3JH____#=KqtH_$3JI____#=KqtH_$3JK____%=KqtH_$3JL____'=KqtH_$3JS____" +
                        "'=KqtH_$8+M____#=KqtH_$99d____%=KqtH_$:Lw____#=LK+x_$:N@____#=KqtG_$:NC____" +
                        "#=KqtG_$:hW____'=KqtH_$:i[____'=KqtH_$:ih____'=KqtH_$:it____'=KqtH_$:kO____" +
                        "'=KqtH_$>*B____'=KqtH_$>hD____+=J^x0_$?lW____'=KqtH_$?ll____'=KqtH_$?lm____" +
                        "%=KqtH_$?mi____'=KqtH_$?mx____'=KqtH_$D7]____#=J_#p_$D@T____#=J_#p_$V<g____" +
                        "'=KqtH";

        headers.add(SET_COOKIE, "bh=\"" + longValue + "\";");
        HttpSetCookie cookie = headers.getSetCookie("bh");
        assertEquals("bh", cookie.name());
        assertEquals(longValue, cookie.value());
    }

    @Test
    public void testIgnoreEmptyDomain() {
        String emptyDomain = "sessionid=OTY4ZDllNTgtYjU3OC00MWRjLTkzMWMtNGUwNzk4MTY0MTUw;Domain=;Path=/";
        headers.add(SET_COOKIE, emptyDomain);
        HttpSetCookie cookie = headers.getSetCookie("sessionid");
        assertThat(cookie.domain()).isEmpty();
    }

    @Test
    public void testIgnoreEmptyPath() {
        String emptyPath = "sessionid=OTY4ZDllNTgtYjU3OC00MWRjLTkzMWMtNGUwNzk4MTY0MTUw;Domain=;Path=";
        headers.add(SET_COOKIE, emptyPath);
        HttpSetCookie cookie = headers.getSetCookie("sessionid");
        assertThat(cookie.path()).isEmpty();
    }
}
