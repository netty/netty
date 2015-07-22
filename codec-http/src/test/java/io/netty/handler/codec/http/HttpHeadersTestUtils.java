/*
 * Copyright 2015 The Netty Project
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.util.internal.StringUtil.COMMA;
import static io.netty.util.internal.StringUtil.DOUBLE_QUOTE;

class HttpHeadersTestUtils {
    public enum HeaderValue {
        UNKNOWN("unknown", 0),
        ONE("one", 1),
        TWO("two", 2),
        THREE("three", 3),
        FOUR("four", 4),
        FIVE("five", 5),
        SIX_QUOTED("six,", 6),
        SEVEN_QUOTED("seven; , GMT", 7),
        EIGHT("eight", 8);

        private final int nr;
        private final String value;
        private CharSequence[] array;

        HeaderValue(final String value, final int nr) {
            this.nr = nr;
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public CharSequence[] asArray() {
            if (array == null) {
                final String[] arr = new String[nr];
                for (int i = 1, y = 0; i <= nr; i++, y++) {
                    arr[y] = of(i).toString();
                }
                array = arr;
            }
            return array;
        }

        public String[] subset(final int from) {
            final int size = from - 1;
            final String[] arr = new String[nr - size];
            System.arraycopy(asArray(), size, arr, 0, arr.length);
            return arr;
        }

        public String subsetAsCsvString(final int from) {
            final String[] subset = subset(from);
            return asCsv(subset);
        }

        public List<CharSequence> asList() {
            return Arrays.<CharSequence>asList(asArray());
        }

        public String asCsv(final CharSequence[] arr) {
            final StringBuilder sb = new StringBuilder();
            int end = arr.length - 1;
            for (int i = 0; i < end; i++) {
                final CharSequence value = arr[i];
                quoted(sb, value).append(COMMA);
            }
            quoted(sb, arr[end]);
            return sb.toString();
        }

        public CharSequence asCsv() {
            return asCsv(asArray());
        }

        private static StringBuilder quoted(final StringBuilder sb, final CharSequence value) {
            if (contains(value, COMMA) && !contains(value, DOUBLE_QUOTE)) {
                return sb.append(DOUBLE_QUOTE).append(value).append(DOUBLE_QUOTE);
            }
            return sb.append(value);
        }

        private static boolean contains(CharSequence value, char c) {
            for (int i = 0; i < value.length(); ++i) {
                if (value.charAt(i) == c) {
                    return true;
                }
            }
            return false;
        }

        private static final Map<Integer, HeaderValue> MAP;

        static {
            final Map<Integer, HeaderValue> map = new HashMap<Integer, HeaderValue>();
            for (HeaderValue v : values()) {
                final int nr = v.nr;
                map.put(Integer.valueOf(nr), v);
            }
            MAP = map;
        }

        public static HeaderValue of(final int nr) {
            final HeaderValue v = MAP.get(Integer.valueOf(nr));
            return v == null ? UNKNOWN : v;
        }
    }
}
