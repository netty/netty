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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2.hpack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class StaticTable {

    private static final String EMPTY = "";

    // Appendix A: Static Table
    // http://tools.ietf.org/html/rfc7541#appendix-A
    private static final List<HeaderField> STATIC_TABLE = Arrays.asList(
    /*  1 */ new HeaderField(":authority", EMPTY),
    /*  2 */ new HeaderField(":method", "GET"),
    /*  3 */ new HeaderField(":method", "POST"),
    /*  4 */ new HeaderField(":path", "/"),
    /*  5 */ new HeaderField(":path", "/index.html"),
    /*  6 */ new HeaderField(":scheme", "http"),
    /*  7 */ new HeaderField(":scheme", "https"),
    /*  8 */ new HeaderField(":status", "200"),
    /*  9 */ new HeaderField(":status", "204"),
    /* 10 */ new HeaderField(":status", "206"),
    /* 11 */ new HeaderField(":status", "304"),
    /* 12 */ new HeaderField(":status", "400"),
    /* 13 */ new HeaderField(":status", "404"),
    /* 14 */ new HeaderField(":status", "500"),
    /* 15 */ new HeaderField("accept-charset", EMPTY),
    /* 16 */ new HeaderField("accept-encoding", "gzip, deflate"),
    /* 17 */ new HeaderField("accept-language", EMPTY),
    /* 18 */ new HeaderField("accept-ranges", EMPTY),
    /* 19 */ new HeaderField("accept", EMPTY),
    /* 20 */ new HeaderField("access-control-allow-origin", EMPTY),
    /* 21 */ new HeaderField("age", EMPTY),
    /* 22 */ new HeaderField("allow", EMPTY),
    /* 23 */ new HeaderField("authorization", EMPTY),
    /* 24 */ new HeaderField("cache-control", EMPTY),
    /* 25 */ new HeaderField("content-disposition", EMPTY),
    /* 26 */ new HeaderField("content-encoding", EMPTY),
    /* 27 */ new HeaderField("content-language", EMPTY),
    /* 28 */ new HeaderField("content-length", EMPTY),
    /* 29 */ new HeaderField("content-location", EMPTY),
    /* 30 */ new HeaderField("content-range", EMPTY),
    /* 31 */ new HeaderField("content-type", EMPTY),
    /* 32 */ new HeaderField("cookie", EMPTY),
    /* 33 */ new HeaderField("date", EMPTY),
    /* 34 */ new HeaderField("etag", EMPTY),
    /* 35 */ new HeaderField("expect", EMPTY),
    /* 36 */ new HeaderField("expires", EMPTY),
    /* 37 */ new HeaderField("from", EMPTY),
    /* 38 */ new HeaderField("host", EMPTY),
    /* 39 */ new HeaderField("if-match", EMPTY),
    /* 40 */ new HeaderField("if-modified-since", EMPTY),
    /* 41 */ new HeaderField("if-none-match", EMPTY),
    /* 42 */ new HeaderField("if-range", EMPTY),
    /* 43 */ new HeaderField("if-unmodified-since", EMPTY),
    /* 44 */ new HeaderField("last-modified", EMPTY),
    /* 45 */ new HeaderField("link", EMPTY),
    /* 46 */ new HeaderField("location", EMPTY),
    /* 47 */ new HeaderField("max-forwards", EMPTY),
    /* 48 */ new HeaderField("proxy-authenticate", EMPTY),
    /* 49 */ new HeaderField("proxy-authorization", EMPTY),
    /* 50 */ new HeaderField("range", EMPTY),
    /* 51 */ new HeaderField("referer", EMPTY),
    /* 52 */ new HeaderField("refresh", EMPTY),
    /* 53 */ new HeaderField("retry-after", EMPTY),
    /* 54 */ new HeaderField("server", EMPTY),
    /* 55 */ new HeaderField("set-cookie", EMPTY),
    /* 56 */ new HeaderField("strict-transport-security", EMPTY),
    /* 57 */ new HeaderField("transfer-encoding", EMPTY),
    /* 58 */ new HeaderField("user-agent", EMPTY),
    /* 59 */ new HeaderField("vary", EMPTY),
    /* 60 */ new HeaderField("via", EMPTY),
    /* 61 */ new HeaderField("www-authenticate", EMPTY)
    );

    private static final Map<String, Integer> STATIC_INDEX_BY_NAME = createMap();

    /**
     * The number of header fields in the static table.
     */
    static final int length = STATIC_TABLE.size();

    /**
     * Return the header field at the given index value.
     */
    static HeaderField getEntry(int index) {
        return STATIC_TABLE.get(index - 1);
    }

    /**
     * Returns the lowest index value for the given header field name in the static table. Returns
     * -1 if the header field name is not in the static table.
     */
    static int getIndex(byte[] name) {
        String nameString = new String(name, 0, name.length, HpackUtil.ISO_8859_1);
        Integer index = STATIC_INDEX_BY_NAME.get(nameString);
        if (index == null) {
            return -1;
        }
        return index;
    }

    /**
     * Returns the index value for the given header field in the static table. Returns -1 if the
     * header field is not in the static table.
     */
    static int getIndex(byte[] name, byte[] value) {
        int index = getIndex(name);
        if (index == -1) {
            return -1;
        }

        // Note this assumes all entries for a given header field are sequential.
        while (index <= length) {
            HeaderField entry = getEntry(index);
            if (!HpackUtil.equals(name, entry.name)) {
                break;
            }
            if (HpackUtil.equals(value, entry.value)) {
                return index;
            }
            index++;
        }

        return -1;
    }

    // create a map of header name to index value to allow quick lookup
    private static Map<String, Integer> createMap() {
        int length = STATIC_TABLE.size();
        HashMap<String, Integer> ret = new HashMap<String, Integer>(length);
        // Iterate through the static table in reverse order to
        // save the smallest index for a given name in the map.
        for (int index = length; index > 0; index--) {
            HeaderField entry = getEntry(index);
            String name = new String(entry.name, 0, entry.name.length, HpackUtil.ISO_8859_1);
            ret.put(name, index);
        }
        return ret;
    }

    // singleton
    private StaticTable() {
    }
}
