/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.util.AsciiString;
import org.junit.jupiter.api.Test;

import static io.netty5.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty5.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty5.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty5.handler.codec.http.headers.HeaderUtils.TOKEN_CHARS;
import static io.netty5.handler.codec.http.headers.HeaderUtils.isTransferEncodingChunked;
import static io.netty5.handler.codec.http.headers.HeaderUtils.pathMatches;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderUtilsTest {
    @Test
    void isTransferEncodingChunkedFalseCases() {
        HttpHeaders headers = HttpHeaders.newHeaders();
        assertTrue(headers.isEmpty());
        assertFalse(isTransferEncodingChunked(headers));

        headers.add("Some-Header", "Some-Value");
        assertFalse(isTransferEncodingChunked(headers));

        headers.add(TRANSFER_ENCODING, "Some-Value");
        assertFalse(isTransferEncodingChunked(headers));

        assertFalse(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("gzip"))
                .add(TRANSFER_ENCODING, "base64")));
    }

    @Test
    void isTransferEncodingChunkedTrueCases() {
        HttpHeaders headers = HttpHeaders.newHeaders();
        assertTrue(headers.isEmpty());
        // lower case
        headers.set(TRANSFER_ENCODING, CHUNKED);
        assertOneTransferEncodingChunked(headers);
        // Capital Case
        headers.set("Transfer-Encoding", "Chunked");
        assertOneTransferEncodingChunked(headers);
        // Random case
        headers.set(TRANSFER_ENCODING, "cHuNkEd");
        assertOneTransferEncodingChunked(headers);

        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("chunked,gzip"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("chunked, gzip"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("gzip, chunked"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("gzip,chunked"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("gzip, chunked, base64"))));

        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("gzip"))
                .add(TRANSFER_ENCODING, "chunked")));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(AsciiString.of("chunked"))
                .add(TRANSFER_ENCODING, "gzip")));
    }

    private static HttpHeaders headersWithContentType(final CharSequence contentType) {
        return HttpHeaders.newHeaders().set(CONTENT_TYPE, contentType);
    }

    private static HttpHeaders headersWithTransferEncoding(final CharSequence contentType) {
        return HttpHeaders.newHeaders().set(TRANSFER_ENCODING, contentType);
    }

    private static void assertOneTransferEncodingChunked(final HttpHeaders headers) {
        assertEquals(1, headers.size());
        assertTrue(isTransferEncodingChunked(headers));
    }

    @Test
    void validateToken() {
        // Make sure the old and new validation logic is equivalent:
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; ++b) {
            final byte value = (byte) (b & 0xff);
            assertEquals(originalValidateTokenLogic(value), TOKEN_CHARS.contains(value),
                    () -> "Unexpected result for byte: " + value);
        }
    }

    @Test
    void pathMatchesTest() {
        assertTrue(pathMatches("/a/b/c", "/a/b/c"));
        assertTrue(pathMatches("/a/b/cxxxx", "/a/b/c"));
        assertTrue(pathMatches(new StringBuilder("/a/b/c"), new StringBuilder("/a/b/c")));
        assertTrue(pathMatches("/a/b/c", new StringBuilder("/a/b/c")));

        assertFalse(pathMatches("xxx/a/b/c", "/a/b/c"));
        assertFalse(pathMatches(new StringBuilder("/a/b/c"), new StringBuilder("/a/B/c")));
    }

    private static boolean originalValidateTokenLogic(final byte value) {
        if (value < '!') {
            return false;
        }
        switch (value) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
            case '{':
            case '}':
            case 127:   // DEL
                return false;
            default:
                return true;
        }
    }
}
