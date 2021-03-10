/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import org.junit.Test;

import java.text.ParseException;
import java.util.Date;

import static org.junit.Assert.*;

public class HttpHeaderDateFormatTest {
    /**
     * This date is set at "06 Nov 1994 08:49:37 GMT" (same used in example in
     * RFC documentation)
     * <p>
     * https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html
     */
    private static final Date DATE = new Date(784111777000L);

    @Test
    public void testParse() throws ParseException {
        HttpHeaderDateFormat format = HttpHeaderDateFormat.get();

        final Date parsedDateWithSingleDigitDay = format.parse("Sun, 6 Nov 1994 08:49:37 GMT");
        assertNotNull(parsedDateWithSingleDigitDay);
        assertEquals(DATE, parsedDateWithSingleDigitDay);

        final Date parsedDateWithDoubleDigitDay = format.parse("Sun, 06 Nov 1994 08:49:37 GMT");
        assertNotNull(parsedDateWithDoubleDigitDay);
        assertEquals(DATE, parsedDateWithDoubleDigitDay);

        final Date parsedDateWithDashSeparatorSingleDigitDay = format.parse("Sunday, 06-Nov-94 08:49:37 GMT");
        assertNotNull(parsedDateWithDashSeparatorSingleDigitDay);
        assertEquals(DATE, parsedDateWithDashSeparatorSingleDigitDay);

        final Date parsedDateWithSingleDoubleDigitDay = format.parse("Sunday, 6-Nov-94 08:49:37 GMT");
        assertNotNull(parsedDateWithSingleDoubleDigitDay);
        assertEquals(DATE, parsedDateWithSingleDoubleDigitDay);

        final Date parsedDateWithoutGMT = format.parse("Sun Nov 6 08:49:37 1994");
        assertNotNull(parsedDateWithoutGMT);
        assertEquals(DATE, parsedDateWithoutGMT);
    }

    @Test
    public void testFormat() {
        HttpHeaderDateFormat format = HttpHeaderDateFormat.get();

        final String formatted = format.format(DATE);
        assertNotNull(formatted);
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", formatted);
    }
}
