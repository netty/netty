/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.handler.codec.UnsupportedValueConverter;
import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class QpackStaticTable {

    static final int NOT_FOUND = -1;

    /**
     * Special mask used to disambiguate exact pair index from
     * name only index and avoid executing lookup twice. Supposed
     * to be used internally. The value should be large enough
     * not to override bits from static table index (current size
     * of the table is 99 elements).
     */
    static final int MASK_NAME_REF = 1 << 10;

    /**
     * Appendix A: Static Table
     * https://tools.ietf.org/html/draft-ietf-quic-qpack-19#appendix-A
     */
    private static final List<QpackHeaderField> STATIC_TABLE = Arrays.asList(
        newEmptyHeaderField(":authority"),
        newHeaderField(":path", "/"),
        newHeaderField("age", "0"),
        newEmptyHeaderField("content-disposition"),
        newHeaderField("content-length", "0"),
        newEmptyHeaderField("cookie"),
        newEmptyHeaderField("date"),
        newEmptyHeaderField("etag"),
        newEmptyHeaderField("if-modified-since"),
        newEmptyHeaderField("if-none-match"),
        newEmptyHeaderField("last-modified"),
        newEmptyHeaderField("link"),
        newEmptyHeaderField("location"),
        newEmptyHeaderField("referer"),
        newEmptyHeaderField("set-cookie"),
        newHeaderField(":method", "CONNECT"),
        newHeaderField(":method", "DELETE"),
        newHeaderField(":method", "GET"),
        newHeaderField(":method", "HEAD"),
        newHeaderField(":method", "OPTIONS"),
        newHeaderField(":method", "POST"),
        newHeaderField(":method", "PUT"),
        newHeaderField(":scheme", "http"),
        newHeaderField(":scheme", "https"),
        newHeaderField(":status", "103"),
        newHeaderField(":status", "200"),
        newHeaderField(":status", "304"),
        newHeaderField(":status", "404"),
        newHeaderField(":status", "503"),
        newHeaderField("accept", "*/*"),
        newHeaderField("accept", "application/dns-message"),
        newHeaderField("accept-encoding", "gzip, deflate, br"),
        newHeaderField("accept-ranges", "bytes"),
        newHeaderField("access-control-allow-headers", "cache-control"),
        newHeaderField("access-control-allow-headers", "content-type"),
        newHeaderField("access-control-allow-origin", "*"),
        newHeaderField("cache-control", "max-age=0"),
        newHeaderField("cache-control", "max-age=2592000"),
        newHeaderField("cache-control", "max-age=604800"),
        newHeaderField("cache-control", "no-cache"),
        newHeaderField("cache-control", "no-store"),
        newHeaderField("cache-control", "public, max-age=31536000"),
        newHeaderField("content-encoding", "br"),
        newHeaderField("content-encoding", "gzip"),
        newHeaderField("content-type", "application/dns-message"),
        newHeaderField("content-type", "application/javascript"),
        newHeaderField("content-type", "application/json"),
        newHeaderField("content-type", "application/x-www-form-urlencoded"),
        newHeaderField("content-type", "image/gif"),
        newHeaderField("content-type", "image/jpeg"),
        newHeaderField("content-type", "image/png"),
        newHeaderField("content-type", "text/css"),
        newHeaderField("content-type", "text/html;charset=utf-8"),
        newHeaderField("content-type", "text/plain"),
        newHeaderField("content-type", "text/plain;charset=utf-8"),
        newHeaderField("range", "bytes=0-"),
        newHeaderField("strict-transport-security", "max-age=31536000"),
        newHeaderField("strict-transport-security", "max-age=31536000;includesubdomains"),
        newHeaderField("strict-transport-security", "max-age=31536000;includesubdomains;preload"),
        newHeaderField("vary", "accept-encoding"),
        newHeaderField("vary", "origin"),
        newHeaderField("x-content-type-options", "nosniff"),
        newHeaderField("x-xss-protection", "1; mode=block"),
        newHeaderField(":status", "100"),
        newHeaderField(":status", "204"),
        newHeaderField(":status", "206"),
        newHeaderField(":status", "302"),
        newHeaderField(":status", "400"),
        newHeaderField(":status", "403"),
        newHeaderField(":status", "421"),
        newHeaderField(":status", "425"),
        newHeaderField(":status", "500"),
        newEmptyHeaderField("accept-language"),
        newHeaderField("access-control-allow-credentials", "FALSE"),
        newHeaderField("access-control-allow-credentials", "TRUE"),
        newHeaderField("access-control-allow-headers", "*"),
        newHeaderField("access-control-allow-methods", "get"),
        newHeaderField("access-control-allow-methods", "get, post, options"),
        newHeaderField("access-control-allow-methods", "options"),
        newHeaderField("access-control-expose-headers", "content-length"),
        newHeaderField("access-control-request-headers", "content-type"),
        newHeaderField("access-control-request-method", "get"),
        newHeaderField("access-control-request-method", "post"),
        newHeaderField("alt-svc", "clear"),
        newEmptyHeaderField("authorization"),
        newHeaderField("content-security-policy", "script-src 'none';object-src 'none';base-uri 'none'"),
        newHeaderField("early-data", "1"),
        newEmptyHeaderField("expect-ct"),
        newEmptyHeaderField("forwarded"),
        newEmptyHeaderField("if-range"),
        newEmptyHeaderField("origin"),
        newHeaderField("purpose", "prefetch"),
        newEmptyHeaderField("server"),
        newHeaderField("timing-allow-origin", "*"),
        newHeaderField("upgrade-insecure-requests", "1"),
        newEmptyHeaderField("user-agent"),
        newEmptyHeaderField("x-forwarded-for"),
        newHeaderField("x-frame-options", "deny"),
        newHeaderField("x-frame-options", "sameorigin"));

    /**
     * The number of header fields in the static table.
     */
    static final int length = STATIC_TABLE.size();

    private static final CharSequenceMap<List<Integer>> STATIC_INDEX_BY_NAME = createMap(length);

    private static QpackHeaderField newEmptyHeaderField(String name) {
        return new QpackHeaderField(AsciiString.cached(name), AsciiString.EMPTY_STRING);
    }

    private static QpackHeaderField newHeaderField(String name, String value) {
        return new QpackHeaderField(AsciiString.cached(name), AsciiString.cached(value));
    }

    /**
     * Return the header field at the given index value.
     * Note that QPACK uses 0-based indexing when HPACK is using 1-based.
     */
    static QpackHeaderField getField(int index) {
        return STATIC_TABLE.get(index);
    }

    /**
     * Returns the lowest index value for the given header field name in the static
     * table. Returns -1 if the header field name is not in the static table.
     */
    static int getIndex(CharSequence name) {
        List<Integer> index = STATIC_INDEX_BY_NAME.get(name);
        if (index == null) {
            return NOT_FOUND;
        }

        return index.get(0);
    }

    /**
     * Returns:
     *    a) the index value for the given header field in the static table (when found);
     *    b) the index value for a given name with a single bit masked (no exact match);
     *    c) -1 if name was not found in the static table.
     */
    static int findFieldIndex(CharSequence name, CharSequence value) {
        final List<Integer> nameIndex = STATIC_INDEX_BY_NAME.get(name);

        // Early return if name not found in the table.
        if (nameIndex == null) {
            return NOT_FOUND;
        }

        // If name was found, check all subsequence elements of the table for exact match.
        for (int index: nameIndex) {
            QpackHeaderField field = STATIC_TABLE.get(index);
            if (QpackUtil.equalsVariableTime(value, field.value)) {
                return index;
            }
        }

        // No exact match was found but we still can reference the name.
        return nameIndex.get(0) | MASK_NAME_REF;
    }

    /**
     * Creates a map CharSequenceMap header name to index value to allow quick lookup.
     */
    @SuppressWarnings("unchecked")
    private static CharSequenceMap<List<Integer>> createMap(int length) {
        CharSequenceMap<List<Integer>> mapping =
            new CharSequenceMap<List<Integer>>(true, UnsupportedValueConverter.<List<Integer>>instance(), length);
        for (int index = 0; index < length; index++) {
            final QpackHeaderField field = getField(index);
            final List<Integer> cursor = mapping.get(field.name);
            if (cursor == null) {
                final List<Integer> holder = new ArrayList<>(16);
                holder.add(index);
                mapping.set(field.name, holder);
            } else {
                cursor.add(index);
            }
        }
        return mapping;
    }

    private QpackStaticTable() {
    }
}
