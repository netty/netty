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

import java.text.ParseException;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

public class HttpHeaderDateFormatTest {
    /**
     * This date is set at "06 Nov 1994 08:49:37 GMT" (same used in example in
     * RFC documentation)
     * <p>
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html
     */
    private static final Date DATE = new Date(784111777000L);

    @Test
    public void testParse() throws ParseException {
        HttpHeaderDateFormat format = HttpHeaderDateFormat.get();

        final Date parsedDateWithSingleDigitDay = format.parse("Sun, 6 Nov 1994 08:49:37 GMT");
        Assert.assertNotNull(parsedDateWithSingleDigitDay);
        Assert.assertEquals(DATE, parsedDateWithSingleDigitDay);

        final Date parsedDateWithDoubleDigitDay = format.parse("Sun, 06 Nov 1994 08:49:37 GMT");
        Assert.assertNotNull(parsedDateWithDoubleDigitDay);
        Assert.assertEquals(DATE, parsedDateWithDoubleDigitDay);

        final Date parsedDateWithDashSeparatorSingleDigitDay = format.parse("Sunday, 06-Nov-94 08:49:37 GMT");
        Assert.assertNotNull(parsedDateWithDashSeparatorSingleDigitDay);
        Assert.assertEquals(DATE, parsedDateWithDashSeparatorSingleDigitDay);

        final Date parsedDateWithSingleDoubleDigitDay = format.parse("Sunday, 6-Nov-94 08:49:37 GMT");
        Assert.assertNotNull(parsedDateWithSingleDoubleDigitDay);
        Assert.assertEquals(DATE, parsedDateWithSingleDoubleDigitDay);

        final Date parsedDateWithoutGMT = format.parse("Sun Nov 6 08:49:37 1994");
        Assert.assertNotNull(parsedDateWithoutGMT);
        Assert.assertEquals(DATE, parsedDateWithoutGMT);
    }

    private Date parseDate(HttpHeaderDateFormat dateFormat, String dateStr) throws ParseException {
        return dateFormat.parse(dateStr);
    }

    @Test
    public void testFormat() {
        HttpHeaderDateFormat format = HttpHeaderDateFormat.get();

        final String formatted = format.format(DATE);
        Assert.assertNotNull(formatted);
        Assert.assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", formatted);
    }
}
