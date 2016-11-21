/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;
import static io.netty.handler.codec.DateFormatter.*;

public class DateFormatterTest {
    /**
     * This date is set at "06 Nov 1994 08:49:37 GMT", from
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html">examples in RFC documentation</a>
     */
    private static final long TIMESTAMP = 784111777000L;
    private static final Date DATE = new Date(TIMESTAMP);

    @Test
    public void testParseWithSingleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sun, 6 Nov 1994 08:49:37 GMT"));
    }

    @Test
    public void testParseWithDoubleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sun, 06 Nov 1994 08:49:37 GMT"));
    }

    @Test
    public void testParseWithDashSeparatorSingleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sunday, 06-Nov-94 08:49:37 GMT"));
    }

    @Test
    public void testParseWithSingleDoubleDigitDay() {
        assertEquals(DATE, parseHttpDate("Sunday, 6-Nov-94 08:49:37 GMT"));
    }

    @Test
    public void testParseWithoutGMT() {
        assertEquals(DATE, parseHttpDate("Sun Nov 6 08:49:37 1994"));
    }

    @Test
    public void testParseWithFunkyTimezone() {
        assertEquals(DATE, parseHttpDate("Sun Nov 6 08:49:37 1994 -0000"));
    }

    @Test
    public void testParseWithSingleDigitHourMinutesAndSecond() {
        assertEquals(DATE, parseHttpDate("Sunday, 6-Nov-94 8:49:37 GMT"));
    }

    @Test
    public void testParseWithSingleDigitTime() {
        assertEquals(DATE, parseHttpDate("Sunday, 6 Nov 1994 8:49:37 GMT"));

        Date _08_09_37 = new Date(TIMESTAMP - 40 * 60 * 1000);
        assertEquals(_08_09_37, parseHttpDate("Sunday, 6 Nov 1994 8:9:37 GMT"));
        assertEquals(_08_09_37, parseHttpDate("Sunday, 6 Nov 1994 8:09:37 GMT"));

        Date _08_09_07 = new Date(TIMESTAMP - (40 * 60 + 30) * 1000);
        assertEquals(_08_09_07, parseHttpDate("Sunday, 6 Nov 1994 8:9:7 GMT"));
        assertEquals(_08_09_07, parseHttpDate("Sunday, 6 Nov 1994 8:9:07 GMT"));
    }

    @Test
    public void testParseMidnight() {
        assertEquals(new Date(784080000000L), parseHttpDate("Sunday, 6 Nov 1994 00:00:00 GMT"));
    }

    @Test
    public void testParseInvalidInput() {
        // missing field
        assertNull(parseHttpDate("Sun, Nov 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 :49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08::37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08:49: GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08:49 GMT"));
        //invalid value
        assertNull(parseHttpDate("Sun, 6 FOO 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 36 Nov 1994 08:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 28:49:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08:69:37 GMT"));
        assertNull(parseHttpDate("Sun, 6 Nov 1994 08:49:67 GMT"));
        //wrong number of digits in timestamp
        assertNull(parseHttpDate("Sunday, 6 Nov 1994 0:0:000 GMT"));
        assertNull(parseHttpDate("Sunday, 6 Nov 1994 0:000:0 GMT"));
        assertNull(parseHttpDate("Sunday, 6 Nov 1994 000:0:0 GMT"));
    }

    @Test
    public void testFormat() {
        assertEquals("Sun, 6 Nov 1994 08:49:37 GMT", format(DATE));
    }
}
