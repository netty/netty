/*
 * Copyright 2019 The Netty Project
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

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Date;

@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class DateFormatter2Benchmark extends AbstractMicrobenchmark {

    @Param({"Sun, 27 Jan 2016 19:18:46 GMT", "Sun, 27 Dec 2016 19:18:46 GMT"})
    String DATE_STRING;

    @Benchmark
    public Date parseHttpHeaderDateFormatterNew() {
        return DateFormatter.parseHttpDate(DATE_STRING);
    }

    /*
    @Benchmark
    public Date parseHttpHeaderDateFormatter() {
        return DateFormatterOld.parseHttpDate(DATE_STRING);
    }
    */

    /*
     * Benchmark                        (DATE_STRING)   Mode  Cnt        Score       Error  Units
     * parseHttpHeaderDateFormatter     Sun, 27 Jan 2016 19:18:46 GMT  thrpt    6  4142781.221 ± 82155.002  ops/s
     * parseHttpHeaderDateFormatter     Sun, 27 Dec 2016 19:18:46 GMT  thrpt    6  3781810.558 ± 38679.061  ops/s
     * parseHttpHeaderDateFormatterNew  Sun, 27 Jan 2016 19:18:46 GMT  thrpt    6  4372569.705 ± 30257.537  ops/s
     * parseHttpHeaderDateFormatterNew  Sun, 27 Dec 2016 19:18:46 GMT  thrpt    6  4339785.100 ± 57542.660  ops/s
     */

    /*Old DateFormatter.tryParseMonth method:
    private boolean tryParseMonth(CharSequence txt, int tokenStart, int tokenEnd) {
        int len = tokenEnd - tokenStart;

        if (len != 3) {
            return false;
        }

        if (matchMonth("Jan", txt, tokenStart)) {
            month = Calendar.JANUARY;
        } else if (matchMonth("Feb", txt, tokenStart)) {
            month = Calendar.FEBRUARY;
        } else if (matchMonth("Mar", txt, tokenStart)) {
            month = Calendar.MARCH;
        } else if (matchMonth("Apr", txt, tokenStart)) {
            month = Calendar.APRIL;
        } else if (matchMonth("May", txt, tokenStart)) {
            month = Calendar.MAY;
        } else if (matchMonth("Jun", txt, tokenStart)) {
            month = Calendar.JUNE;
        } else if (matchMonth("Jul", txt, tokenStart)) {
            month = Calendar.JULY;
        } else if (matchMonth("Aug", txt, tokenStart)) {
            month = Calendar.AUGUST;
        } else if (matchMonth("Sep", txt, tokenStart)) {
            month = Calendar.SEPTEMBER;
        } else if (matchMonth("Oct", txt, tokenStart)) {
            month = Calendar.OCTOBER;
        } else if (matchMonth("Nov", txt, tokenStart)) {
            month = Calendar.NOVEMBER;
        } else if (matchMonth("Dec", txt, tokenStart)) {
            month = Calendar.DECEMBER;
        } else {
            return false;
        }

        return true;
    }
    */

}
