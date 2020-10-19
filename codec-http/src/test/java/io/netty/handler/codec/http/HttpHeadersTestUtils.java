/*
 * Copyright 2015 The Netty Project
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.util.internal.StringUtil.COMMA;
import static io.netty.util.internal.StringUtil.DOUBLE_QUOTE;

/**
 * Utility methods for {@link HttpHeaders} related unit tests.
 */
public final class HttpHeadersTestUtils {
    enum HeaderValue {
        UNKNOWN("Unknown", 0),
        ONE("One", 1),
        TWO("Two", 2),
        THREE("Three", 3),
        FOUR("Four", 4),
        FIVE("Five", 5),
        SIX_QUOTED("Six,", 6),
        SEVEN_QUOTED("Seven; , GMT", 7),
        EIGHT("Eight", 8);

        private final int nr;
        private final String value;
        private List<CharSequence> array;

        HeaderValue(final String value, final int nr) {
            this.nr = nr;
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public List<CharSequence> asList() {
            if (array == null) {
                List<CharSequence> list = new ArrayList<CharSequence>(nr);
                for (int i = 1; i <= nr; i++) {
                    list.add(of(i).toString());
                }
                array = list;
            }
            return array;
        }

        public List<CharSequence> subset(int from) {
            assert from > 0;
            --from;
            final int size = nr - from;
            final int end = from + size;
            List<CharSequence> list = new ArrayList<CharSequence>(size);
            List<CharSequence> fullList = asList();
            for (int i = from; i < end; ++i) {
                list.add(fullList.get(i));
            }
            return list;
        }

        public String subsetAsCsvString(final int from) {
            final List<CharSequence> subset = subset(from);
            return asCsv(subset);
        }

        public String asCsv(final List<CharSequence> arr) {
            if (arr == null || arr.isEmpty()) {
                return "";
            }
            final StringBuilder sb = new StringBuilder(arr.size() * 10);
            final int end = arr.size() - 1;
            for (int i = 0; i < end; ++i) {
                quoted(sb, arr.get(i)).append(COMMA);
            }
            quoted(sb, arr.get(end));
            return sb.toString();
        }

        public CharSequence asCsv() {
            return asCsv(asList());
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

    public static CharSequence of(String s) {
        return s;
    }

    private HttpHeadersTestUtils() { }
}
