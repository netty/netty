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

import io.netty.handler.codec.http.HttpHeaderDateFormat;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
public class DateFormatterBenchmark {

    private static final String DATE_STRING = "Sun, 27 Nov 2016 19:18:46 GMT";
    private static final Date DATE = new Date(784111777000L);

    @Benchmark
    public Date parseHttpHeaderDateFormatter() {
        return DateFormatter.parseHttpDate(DATE_STRING);
    }

    @Benchmark
    public Date parseHttpHeaderDateFormat() throws Exception {
        return HttpHeaderDateFormat.get().parse(DATE_STRING);
    }

    @Benchmark
    public String formatHttpHeaderDateFormatter() {
        return DateFormatter.format(DATE);
    }

    @Benchmark
    public String formatHttpHeaderDateFormat() throws Exception {
        return HttpHeaderDateFormat.get().format(DATE);
    }
}
