/*
 * Copyright 2017 The Netty Project
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
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.concurrent.TimeUnit;

import static io.netty.util.internal.ObjectUtil.*;
import static io.netty.util.internal.StringUtil.*;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class EscapeCsvBenchmark extends AbstractMicrobenchmark {

    private static final String value1024;
    private static final String value1024commaAtEnd;
    static {
        StringBuilder s1024 = new StringBuilder(1024);
        while (s1024.length() < 1024) {
            s1024.append('A' + s1024.length() % 10);
        }
        value1024 = s1024.toString();
        value1024commaAtEnd = value1024 + ',';
    }

    @Param("netty")
    private String value;

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder()
                    .param("value", "netty")
                    .param("value", "\"123\"", "need\"escape", "need,quotes", "  trim-me  ", "short-comma-ended,")
                    .param("value", value1024)
                    .param("value", value1024commaAtEnd);
    }

    private static CharSequence escapeCsvOld(CharSequence value, boolean trimWhiteSpace) {
        int length = checkNotNull(value, "value").length();
        if (length == 0) {
            return value;
        }

        int start = 0;
        int last = length - 1;
        boolean trimmed = false;
        if (trimWhiteSpace) {
            start = indexOfFirstNonOwsChar(value, length);
            if (start == length) {
                return EMPTY_STRING;
            }
            last = indexOfLastNonOwsChar(value, start, length);
            trimmed = start > 0 || last < length - 1;
            if (trimmed) {
                length = last - start + 1;
            }
        }

        StringBuilder result = new StringBuilder(length + 7);
        boolean quoted = isDoubleQuote(value.charAt(start)) && isDoubleQuote(value.charAt(last)) && length != 1;
        boolean foundSpecialCharacter = false;
        boolean escapedDoubleQuote = false;
        for (int i = start; i <= last; i++) {
            char current = value.charAt(i);
            switch (current) {
            case DOUBLE_QUOTE:
                if (i == start || i == last) {
                    if (!quoted) {
                        result.append(DOUBLE_QUOTE);
                    } else {
                        continue;
                    }
                } else {
                    boolean isNextCharDoubleQuote = isDoubleQuote(value.charAt(i + 1));
                    if (!isDoubleQuote(value.charAt(i - 1)) &&
                        (!isNextCharDoubleQuote || i + 1 == last)) {
                        result.append(DOUBLE_QUOTE);
                        escapedDoubleQuote = true;
                    }
                    break;
                }
            case LINE_FEED:
            case CARRIAGE_RETURN:
            case COMMA:
                foundSpecialCharacter = true;
            }
            result.append(current);
        }

        if (escapedDoubleQuote || foundSpecialCharacter && !quoted) {
            return quote(result);
        }
        if (trimmed) {
            return quoted? quote(result) : result;
        }
        return value;
    }

    private static StringBuilder quote(StringBuilder builder) {
        return builder.insert(0, DOUBLE_QUOTE).append(DOUBLE_QUOTE);
    }

    private static boolean isDoubleQuote(char c) {
        return c == DOUBLE_QUOTE;
    }

    private static int indexOfFirstNonOwsChar(CharSequence value, int length) {
        int i = 0;
        while (i < length && isOws(value.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int indexOfLastNonOwsChar(CharSequence value, int start, int length) {
        int i = length - 1;
        while (i > start && isOws(value.charAt(i))) {
            i--;
        }
        return i;
    }

    private static boolean isOws(char c) {
        return c == SPACE || c == TAB;
    }

    @Benchmark
    public CharSequence escapeCsvOld() {
        return escapeCsvOld(value, true);
    }

    @Benchmark
    public CharSequence escapeCsvNew() {
        return escapeCsv(value, true);
    }

}
