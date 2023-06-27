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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.List;

import static io.netty.handler.codec.http2.HpackUtil.equalsVariableTime;

final class HpackStaticTable {

    static final int NOT_FOUND = -1;

    // Appendix A: Static Table
    // https://tools.ietf.org/html/rfc7541#appendix-A
    private static final List<HpackHeaderField> STATIC_TABLE = Arrays.asList(
    /*  1 */ newEmptyHeaderField(":authority"),
    /*  2 */ newHeaderField(":method", "GET"),
    /*  3 */ newHeaderField(":method", "POST"),
    /*  4 */ newHeaderField(":path", "/"),
    /*  5 */ newHeaderField(":path", "/index.html"),
    /*  6 */ newHeaderField(":scheme", "http"),
    /*  7 */ newHeaderField(":scheme", "https"),
    /*  8 */ newHeaderField(":status", "200"),
    /*  9 */ newHeaderField(":status", "204"),
    /* 10 */ newHeaderField(":status", "206"),
    /* 11 */ newHeaderField(":status", "304"),
    /* 12 */ newHeaderField(":status", "400"),
    /* 13 */ newHeaderField(":status", "404"),
    /* 14 */ newHeaderField(":status", "500"),
    /* 15 */ newEmptyHeaderField("accept-charset"),
    /* 16 */ newHeaderField("accept-encoding", "gzip, deflate"),
    /* 17 */ newEmptyHeaderField("accept-language"),
    /* 18 */ newEmptyHeaderField("accept-ranges"),
    /* 19 */ newEmptyHeaderField("accept"),
    /* 20 */ newEmptyHeaderField("access-control-allow-origin"),
    /* 21 */ newEmptyHeaderField("age"),
    /* 22 */ newEmptyHeaderField("allow"),
    /* 23 */ newEmptyHeaderField("authorization"),
    /* 24 */ newEmptyHeaderField("cache-control"),
    /* 25 */ newEmptyHeaderField("content-disposition"),
    /* 26 */ newEmptyHeaderField("content-encoding"),
    /* 27 */ newEmptyHeaderField("content-language"),
    /* 28 */ newEmptyHeaderField("content-length"),
    /* 29 */ newEmptyHeaderField("content-location"),
    /* 30 */ newEmptyHeaderField("content-range"),
    /* 31 */ newEmptyHeaderField("content-type"),
    /* 32 */ newEmptyHeaderField("cookie"),
    /* 33 */ newEmptyHeaderField("date"),
    /* 34 */ newEmptyHeaderField("etag"),
    /* 35 */ newEmptyHeaderField("expect"),
    /* 36 */ newEmptyHeaderField("expires"),
    /* 37 */ newEmptyHeaderField("from"),
    /* 38 */ newEmptyHeaderField("host"),
    /* 39 */ newEmptyHeaderField("if-match"),
    /* 40 */ newEmptyHeaderField("if-modified-since"),
    /* 41 */ newEmptyHeaderField("if-none-match"),
    /* 42 */ newEmptyHeaderField("if-range"),
    /* 43 */ newEmptyHeaderField("if-unmodified-since"),
    /* 44 */ newEmptyHeaderField("last-modified"),
    /* 45 */ newEmptyHeaderField("link"),
    /* 46 */ newEmptyHeaderField("location"),
    /* 47 */ newEmptyHeaderField("max-forwards"),
    /* 48 */ newEmptyHeaderField("proxy-authenticate"),
    /* 49 */ newEmptyHeaderField("proxy-authorization"),
    /* 50 */ newEmptyHeaderField("range"),
    /* 51 */ newEmptyHeaderField("referer"),
    /* 52 */ newEmptyHeaderField("refresh"),
    /* 53 */ newEmptyHeaderField("retry-after"),
    /* 54 */ newEmptyHeaderField("server"),
    /* 55 */ newEmptyHeaderField("set-cookie"),
    /* 56 */ newEmptyHeaderField("strict-transport-security"),
    /* 57 */ newEmptyHeaderField("transfer-encoding"),
    /* 58 */ newEmptyHeaderField("user-agent"),
    /* 59 */ newEmptyHeaderField("vary"),
    /* 60 */ newEmptyHeaderField("via"),
    /* 61 */ newEmptyHeaderField("www-authenticate")
    );

    private static HpackHeaderField newEmptyHeaderField(String name) {
        return new HpackHeaderField(AsciiString.cached(name), AsciiString.EMPTY_STRING);
    }

    private static HpackHeaderField newHeaderField(String name, String value) {
        return new HpackHeaderField(AsciiString.cached(name), AsciiString.cached(value));
    }

    // The table size and bit shift are chosen so that each hash bucket contains a single header name.
    private static final int HEADER_NAMES_TABLE_SIZE = 1 << 9;

    private static final int HEADER_NAMES_TABLE_SHIFT = PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? 22 : 18;

    // A table mapping header names to their associated indexes.
    private static final HeaderNameIndex[] HEADER_NAMES = new HeaderNameIndex[HEADER_NAMES_TABLE_SIZE];
    static {
        // Iterate through the static table in reverse order to
        // save the smallest index for a given name in the table.
        for (int index = STATIC_TABLE.size(); index > 0; index--) {
            HpackHeaderField entry = getEntry(index);
            int bucket = headerNameBucket(entry.name);
            HeaderNameIndex tableEntry = HEADER_NAMES[bucket];
            if (tableEntry != null && !equalsVariableTime(tableEntry.name, entry.name)) {
                // Can happen if AsciiString.hashCode changes
                throw new IllegalStateException("Hash bucket collision between " +
                  tableEntry.name + " and " + entry.name);
            }
            HEADER_NAMES[bucket] = new HeaderNameIndex(entry.name, index, entry.value.length() == 0);
        }
    }

    // The table size and bit shift are chosen so that each hash bucket contains a single header.
    private static final int HEADERS_WITH_NON_EMPTY_VALUES_TABLE_SIZE = 1 << 6;

    private static final int HEADERS_WITH_NON_EMPTY_VALUES_TABLE_SHIFT =
      PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? 0 : 6;

    // A table mapping headers with non-empty values to their associated indexes.
    private static final HeaderIndex[] HEADERS_WITH_NON_EMPTY_VALUES =
      new HeaderIndex[HEADERS_WITH_NON_EMPTY_VALUES_TABLE_SIZE];
    static {
        for (int index = STATIC_TABLE.size(); index > 0; index--) {
            HpackHeaderField entry = getEntry(index);
            if (entry.value.length() > 0) {
                int bucket = headerBucket(entry.value);
                HeaderIndex tableEntry = HEADERS_WITH_NON_EMPTY_VALUES[bucket];
                if (tableEntry != null) {
                    // Can happen if AsciiString.hashCode changes
                    throw new IllegalStateException("Hash bucket collision between " +
                      tableEntry.value + " and " + entry.value);
                }
                HEADERS_WITH_NON_EMPTY_VALUES[bucket] = new HeaderIndex(entry.name, entry.value, index);
            }
        }
    }

    /**
     * The number of header fields in the static table.
     */
    static final int length = STATIC_TABLE.size();

    /**
     * Return the header field at the given index value.
     */
    static HpackHeaderField getEntry(int index) {
        return STATIC_TABLE.get(index - 1);
    }

    /**
     * Returns the lowest index value for the given header field name in the static table. Returns
     * -1 if the header field name is not in the static table.
     */
    static int getIndex(CharSequence name) {
        HeaderNameIndex entry = getEntry(name);
        return entry == null ? NOT_FOUND : entry.index;
    }

    /**
     * Returns the index value for the given header field in the static table. Returns -1 if the
     * header field is not in the static table.
     */
    static int getIndexInsensitive(CharSequence name, CharSequence value) {
        if (value.length() == 0) {
            HeaderNameIndex entry = getEntry(name);
            return entry == null || !entry.emptyValue ? NOT_FOUND : entry.index;
        }
        int bucket = headerBucket(value);
        HeaderIndex header = HEADERS_WITH_NON_EMPTY_VALUES[bucket];
        if (header == null) {
            return NOT_FOUND;
        }
        if (equalsVariableTime(header.name, name) && equalsVariableTime(header.value, value)) {
            return header.index;
        }
        return NOT_FOUND;
    }

    private static HeaderNameIndex getEntry(CharSequence name) {
        int bucket = headerNameBucket(name);
        HeaderNameIndex entry = HEADER_NAMES[bucket];
        if (entry == null) {
            return null;
        }
        return equalsVariableTime(entry.name, name) ? entry : null;
    }

    private static int headerNameBucket(CharSequence name) {
        return bucket(name, HEADER_NAMES_TABLE_SHIFT, HEADER_NAMES_TABLE_SIZE - 1);
    }

    private static int headerBucket(CharSequence value) {
        return bucket(value, HEADERS_WITH_NON_EMPTY_VALUES_TABLE_SHIFT, HEADERS_WITH_NON_EMPTY_VALUES_TABLE_SIZE - 1);
    }

    private static int bucket(CharSequence s, int shift, int mask) {
        return (AsciiString.hashCode(s) >> shift) & mask;
    }

    private static final class HeaderNameIndex {
        final CharSequence name;
        final int index;
        final boolean emptyValue;

        HeaderNameIndex(CharSequence name, int index, boolean emptyValue) {
            this.name = name;
            this.index = index;
            this.emptyValue = emptyValue;
        }
    }

    private static final class HeaderIndex {
        final CharSequence name;
        final CharSequence value;
        final int index;

        HeaderIndex(CharSequence name, CharSequence value, int index) {
            this.name = name;
            this.value = value;
            this.index = index;
        }
    }

    // singleton
    private HpackStaticTable() {
    }
}
