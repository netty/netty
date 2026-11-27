/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static io.netty.handler.codec.DateFormatter.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DateFormatterTest {
    /**
     * This date is set at "06 Nov 1994 08:49:37 GMT", from
     * <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html">examples in RFC documentation</a>
     */
    private static final long TIMESTAMP = 784111777000L;
    private static final Date DATE = new Date(TIMESTAMP);

    @Test
    public void testParseWithSingleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sun, 6 Nov 1994 08:49:37 GMT"));
    }

    @Test
    public void testParseHttpDateSubstringDoesNotOverrunEnd() {
        // Set-Cookie header format with the date anywhere but last (RFC 6265 tokens are unordered).
        String dateText = "Sun 08:49:37 06 Nov 1994";
        assertEquals(DATE, parseHttpDate(dateText));
        assertEquals(DATE, parseHttpDate(dateText + "; Path=/", 0, dateText.length()));
    }

    @Test
    public void testParseWithDoubleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sun, 06 Nov 1994 08:49:37 GMT"));
    }

    @Test
    public void testParseWithDashSeparatorSingleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sunday, 6-Nov-94 08:49:37 GMT"));
    }

    @Test
    public void testParseWithDashSeparatorDoubleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sunday, 06-Nov-94 08:49:37 GMT"));
    }

    @Test
    public void testParseCookieDateWithFullMonthName() {
        // RFC 6265, section 5.1.1: month = ( "jan" / ... / "dec" ) *OCTET
        String rfc1123 = "Sunday, 06 November 1994 08:49:37 GMT";
        String rfc850 = "Sunday, 06-November-94 08:49:37 GMT";
        assertEquals(DATE, parseCookieDate(rfc1123, 0, rfc1123.length()));
        assertEquals(DATE, parseCookieDate(rfc850, 0, rfc850.length()));
        assertEquals(DATE, parseCookieDate("Sun, 06 Nov 1994 08:49:37 GMT", 0, 29));
    }

    @Test
    public void testParseHttpDateRejectsFullMonthName() {
        assertNull(parseHttpDate("Sunday, 06 November 1994 08:49:37 GMT"));
        // a cookie date must not leave the formatter accepting long months for the next HTTP date
        String cookieDate = "Sunday, 06 November 1994 08:49:37 GMT";
        assertEquals(DATE, parseCookieDate(cookieDate, 0, cookieDate.length()));
        assertNull(parseHttpDate(cookieDate));
    }

    @Test
    public void testParseWithoutGMT() {
        assertEquals(DATE, parseHttpDate("Sun Nov 06 08:49:37 1994"));
    }

    @Test
    public void testParseWithFunkyTimezone() {
        assertEquals(DATE, parseHttpDate("Sun Nov 06 08:49:37 1994 -0000"));
    }

    @Test
    public void testParseWithSingleDigitHourMinutesAndSecond() {
        assertEquals(DATE, parseHttpDate("Sunday, 06-Nov-94 8:49:37 GMT"));
    }

    @Test
    public void testParseWithSingleDigitTime() {
        assertEquals(DATE, parseHttpDate("Sunday, 06 Nov 1994 8:49:37 GMT"));

        Date _08_09_37 = new Date(TIMESTAMP - 40 * 60 * 1000);
        assertEquals(_08_09_37, parseHttpDate("Sunday, 06 Nov 1994 8:9:37 GMT"));
        assertEquals(_08_09_37, parseHttpDate("Sunday, 06 Nov 1994 8:09:37 GMT"));

        Date _08_09_07 = new Date(TIMESTAMP - (40 * 60 + 30) * 1000);
        assertEquals(_08_09_07, parseHttpDate("Sunday, 06 Nov 1994 8:9:7 GMT"));
        assertEquals(_08_09_07, parseHttpDate("Sunday, 06 Nov 1994 8:9:07 GMT"));
    }

    @Test
    public void testParseMidnight() {
        assertEquals(new Date(784080000000L), parseHttpDate("Sunday, 06 Nov 1994 00:00:00 GMT"));
    }

    @Test
    public void testParseInvalidInput() {
        // missing field
        assertNull(parseHttpDate("Sun, Nov 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 :49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08::37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08:49: GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08:49 GMT"));
        //invalid value
        assertNull(parseHttpDate("Sun, 06 FOO 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 36 Nov 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 28:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08:69:37 GMT"));
        assertNull(parseHttpDate("Sun, 06 Nov 1994 08:49:67 GMT"));
        //wrong number of digits in timestamp
        assertNull(parseHttpDate("Sunday, 06 Nov 1994 0:0:000 GMT"));
        assertNull(parseHttpDate("Sunday, 06 Nov 1994 0:000:0 GMT"));
        assertNull(parseHttpDate("Sunday, 06 Nov 1994 000:0:0 GMT"));
    }

    @Test
    public void testParseNonExistentDate() {
        // RFC 6265, section 5.1.1: if no such date exists, fail to parse the cookie-date.
        assertNull(parseHttpDate("Wed, 31 Feb 2016 08:49:37 GMT"));
        assertNull(parseHttpDate("Wed, 30 Feb 2016 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 29 Feb 2015 08:49:37 GMT"));
        assertNull(parseHttpDate("Thu, 31 Apr 2016 08:49:37 GMT"));
        assertNull(parseHttpDate("Thu, 31-Nov-94 08:49:37 GMT"));
        // century years are leap years only if divisible by 400
        assertNull(parseHttpDate("Thu, 29 Feb 1900 08:49:37 GMT"));
        assertNull(parseHttpDate("Sat, 29 Feb 2100 08:49:37 GMT"));
    }

    @Test
    public void testParseLastDayOfMonth() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(2016, Calendar.FEBRUARY, 29, 8, 49, 37);
        assertEquals(cal.getTime(), parseHttpDate("Mon, 29 Feb 2016 08:49:37 GMT"));
        cal.clear();
        cal.set(2016, Calendar.DECEMBER, 31, 8, 49, 37);
        assertEquals(cal.getTime(), parseHttpDate("Sat, 31 Dec 2016 08:49:37 GMT"));
        cal.clear();
        cal.set(2000, Calendar.FEBRUARY, 29, 8, 49, 37);
        assertEquals(cal.getTime(), parseHttpDate("Tue, 29 Feb 2000 08:49:37 GMT"));
    }

    @Test
    public void testFormat() {
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", format(DATE));
    }

    @Test
    public void testAppend() {
        StringBuilder sb = new StringBuilder();
        append(DATE, sb);
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", sb.toString());
    }

    @Test
    public void testParseAllMonths() {
        assertEquals(Calendar.JANUARY, getMonth(parseHttpDate("Sun, 06 Jan 1994 08:49:37 GMT")));
        assertEquals(Calendar.FEBRUARY, getMonth(parseHttpDate("Sun, 06 Feb 1994 08:49:37 GMT")));
        assertEquals(Calendar.MARCH, getMonth(parseHttpDate("Sun, 06 Mar 1994 08:49:37 GMT")));
        assertEquals(Calendar.APRIL, getMonth(parseHttpDate("Sun, 06 Apr 1994 08:49:37 GMT")));
        assertEquals(Calendar.MAY, getMonth(parseHttpDate("Sun, 06 May 1994 08:49:37 GMT")));
        assertEquals(Calendar.JUNE, getMonth(parseHttpDate("Sun, 06 Jun 1994 08:49:37 GMT")));
        assertEquals(Calendar.JULY, getMonth(parseHttpDate("Sun, 06 Jul 1994 08:49:37 GMT")));
        assertEquals(Calendar.AUGUST, getMonth(parseHttpDate("Sun, 06 Aug 1994 08:49:37 GMT")));
        assertEquals(Calendar.SEPTEMBER, getMonth(parseHttpDate("Sun, 06 Sep 1994 08:49:37 GMT")));
        assertEquals(Calendar.OCTOBER, getMonth(parseHttpDate("Sun Oct 06 08:49:37 1994")));
        assertEquals(Calendar.NOVEMBER, getMonth(parseHttpDate("Sun Nov 06 08:49:37 1994")));
        assertEquals(Calendar.DECEMBER, getMonth(parseHttpDate("Sun Dec 06 08:49:37 1994")));
    }

    private static int getMonth(Date referenceDate) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(referenceDate);
        return cal.get(Calendar.MONTH);
    }
}
