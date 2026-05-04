/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName;
import io.netty.util.AsciiString;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map.Entry;
import java.util.stream.Stream;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.util.AsciiString.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultHttp2HeadersTest {

    @Test
    public void nullHeaderNameNotAllowed() {
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                new DefaultHttp2Headers().add(null, "foo");
            }
        });
    }

    @Test
    public void emptyHeaderNameNotAllowed() {
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                new DefaultHttp2Headers().add(StringUtil.EMPTY_STRING, "foo");
            }
        });
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIterating() {
        Http2Headers headers = newHeaders();

        verifyPseudoHeadersFirst(headers);
        verifyAllPseudoHeadersPresent(headers);
    }

    @Test
    public void testPseudoHeadersWithRemovePreservesPseudoIterationOrder() {
        Http2Headers headers = newHeaders();

        Http2Headers nonPseudoHeaders = new DefaultHttp2Headers();
        for (Entry<CharSequence, CharSequence> entry : headers) {
            if (entry.getKey().length() == 0 || entry.getKey().charAt(0) != ':' &&
                !nonPseudoHeaders.contains(entry.getKey())) {
                nonPseudoHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        assertFalse(nonPseudoHeaders.isEmpty());

        // Remove all the non-pseudo headers and verify
        for (Entry<CharSequence, CharSequence> nonPseudoHeaderEntry : nonPseudoHeaders) {
            assertTrue(headers.remove(nonPseudoHeaderEntry.getKey()));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }

        // Add back all non-pseudo headers
        for (Entry<CharSequence, CharSequence> nonPseudoHeaderEntry : nonPseudoHeaders) {
            headers.add(nonPseudoHeaderEntry.getKey(), of("goo"));
            verifyPseudoHeadersFirst(headers);
            verifyAllPseudoHeadersPresent(headers);
        }
    }

    @Test
    public void testPseudoHeadersWithClearDoesNotLeak() {
        Http2Headers headers = newHeaders();

        assertFalse(headers.isEmpty());
        headers.clear();
        assertTrue(headers.isEmpty());

        // Combine 2 headers together, make sure pseudo headers stay up front.
        headers.add("name1", "value1").scheme("nothing");
        verifyPseudoHeadersFirst(headers);

        Http2Headers other = new DefaultHttp2Headers().add("name2", "value2").authority("foo");
        verifyPseudoHeadersFirst(other);

        headers.add(other);
        verifyPseudoHeadersFirst(headers);

        // Make sure the headers are what we expect them to be, and no leaking behind the scenes.
        assertEquals(4, headers.size());
        assertEquals("value1", headers.get("name1"));
        assertEquals("value2", headers.get("name2"));
        assertEquals("nothing", headers.scheme());
        assertEquals("foo", headers.authority());
    }

    @Test
    public void testSetHeadersOrdersPseudoHeadersCorrectly() {
        Http2Headers headers = newHeaders();
        Http2Headers other = new DefaultHttp2Headers().add("name2", "value2").authority("foo");

        headers.set(other);
        verifyPseudoHeadersFirst(headers);
        assertEquals(other.size(), headers.size());
        assertEquals("foo", headers.authority());
        assertEquals("value2", headers.get("name2"));
    }

    @Test
    public void testSetAllOrdersPseudoHeadersCorrectly() {
        Http2Headers headers = newHeaders();
        Http2Headers other = new DefaultHttp2Headers().add("name2", "value2").authority("foo");

        int headersSizeBefore = headers.size();
        headers.setAll(other);
        verifyPseudoHeadersFirst(headers);
        verifyAllPseudoHeadersPresent(headers);
        assertEquals(headersSizeBefore + 1, headers.size());
        assertEquals("foo", headers.authority());
        assertEquals("value2", headers.get("name2"));
    }

    @Test
    public void testHeaderNameValidation() {
        final Http2Headers headers = newHeaders();

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                headers.add(of("Foo"), of("foo"));
            }
        });
    }

    @Test
    public void testClearResetsPseudoHeaderDivision() {
        DefaultHttp2Headers http2Headers = new DefaultHttp2Headers();
        http2Headers.method("POST");
        http2Headers.set("some", "value");
        http2Headers.clear();
        http2Headers.method("GET");
        assertEquals(1, http2Headers.names().size());
    }

    @Test
    public void testContainsNameAndValue() {
        Http2Headers headers = newHeaders();
        assertTrue(headers.contains("name1", "value2"));
        assertFalse(headers.contains("name1", "Value2"));
        assertTrue(headers.contains("2name", "Value3", true));
        assertFalse(headers.contains("2name", "Value3", false));
    }

    @Test
    public void testContainsName() {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.add(CONTENT_LENGTH, "36");
        assertFalse(headers.contains("Content-Length"));
        assertTrue(headers.contains("content-length"));
        assertTrue(headers.contains(CONTENT_LENGTH));
        headers.remove(CONTENT_LENGTH);
        assertFalse(headers.contains("Content-Length"));
        assertFalse(headers.contains("content-length"));
        assertFalse(headers.contains(CONTENT_LENGTH));

        assertFalse(headers.contains("non-existent-name"));
        assertFalse(headers.contains(new AsciiString("non-existent-name")));
    }

    @Test
    void setMustOverwritePseudoHeaders() {
        Http2Headers headers = newHeaders();
        // The headers are already populated with pseudo headers.
        headers.method(of("GET"));
        headers.path(of("/index2.html"));
        headers.status(of("101"));
        headers.authority(of("github.com"));
        headers.scheme(of("http"));
        headers.set(of(":protocol"), of("http"));
        assertEquals(of("GET"), headers.method());
        assertEquals(of("/index2.html"), headers.path());
        assertEquals(of("101"), headers.status());
        assertEquals(of("github.com"), headers.authority());
        assertEquals(of("http"), headers.scheme());
    }

    @ParameterizedTest(name = "{displayName} [{index}] name={0} value={1}")
    @CsvSource(value = {"upgrade,protocol1", "connection,close", "keep-alive,timeout=5", "proxy-connection,close",
            "transfer-encoding,chunked", "te,something-else"})
    void possibleToAddConnectionHeaders(String name, String value) {
        Http2Headers headers = newHeaders();
        headers.add(name, value);
        assertTrue(headers.contains(name, value));
    }

    @ParameterizedTest(name = "{displayName} [{index}] name={0} value={1}")
    @MethodSource("invalidPseudoHeaders")
    void headerValueValidation(String name, String value) {
        // The second `true` parameter enables header value validation:
        Http2Headers headers = new DefaultHttp2Headers(true, true, 10);
        Class<? extends Exception> expectedType =
                value.isEmpty() ? Http2Exception.class : IllegalArgumentException.class;
        assertThrows(expectedType, () -> headers.add(name, value));
    }

    static Stream<Arguments> invalidPseudoHeaders() {
        return Stream.of(
                // NUL character (0x00)
                Arguments.of(":method", "GET\0"),
                Arguments.of(":method", "\0GET"),
                Arguments.of(":scheme", "http\0"),
                Arguments.of(":path", "/path\0with\0nulls"),
                Arguments.of(":authority", "example.com\0"),

                // CR character (0x0D)
                Arguments.of(":method", "GET\r"),
                Arguments.of(":scheme", "http\r"),
                Arguments.of(":path", "/path\r"),
                Arguments.of(":authority", "example.com\r"),

                // LF character (0x0A)
                Arguments.of(":method", "GET\n"),
                Arguments.of(":scheme", "http\n"),
                Arguments.of(":path", "/path\n"),
                Arguments.of(":authority", "example.com\n"),

                // CR LF sequence
                Arguments.of(":method", "GET\r\n"),
                Arguments.of(":scheme", "http\r\n"),
                Arguments.of(":path", "/path\r\n"),
                Arguments.of(":authority", "example.com\r\n"),

                // DEL character (0x7F)
                Arguments.of(":method", "GET\u007F"),
                Arguments.of(":scheme", "http\u007F"),
                Arguments.of(":path", "/path\u007F"),
                Arguments.of(":authority", "example.com\u007F"),

                // Control characters 0x01-0x08
                Arguments.of(":method", "GET\u0001"),
                Arguments.of(":method", "GET\u0002"),
                Arguments.of(":method", "GET\u0003"),
                Arguments.of(":method", "GET\u0004"),
                Arguments.of(":method", "GET\u0005"),
                Arguments.of(":method", "GET\u0006"),
                Arguments.of(":method", "GET\u0007"),
                Arguments.of(":method", "GET\u0008"),

                // Control characters 0x0B, 0x0C
                Arguments.of(":method", "GET\u000B"),
                Arguments.of(":method", "GET\u000C"),

                // Control characters 0x0E-0x1F
                Arguments.of(":method", "GET\u000E"),
                Arguments.of(":method", "GET\u000F"),
                Arguments.of(":method", "GET\u0010"),
                Arguments.of(":method", "GET\u0011"),
                Arguments.of(":method", "GET\u0012"),
                Arguments.of(":method", "GET\u0013"),
                Arguments.of(":method", "GET\u0014"),
                Arguments.of(":method", "GET\u0015"),
                Arguments.of(":method", "GET\u0016"),
                Arguments.of(":method", "GET\u0017"),
                Arguments.of(":method", "GET\u0018"),
                Arguments.of(":method", "GET\u0019"),
                Arguments.of(":method", "GET\u001A"),
                Arguments.of(":method", "GET\u001B"),
                Arguments.of(":method", "GET\u001C"),
                Arguments.of(":method", "GET\u001D"),
                Arguments.of(":method", "GET\u001E"),
                Arguments.of(":method", "GET\u001F"),

                // Multiple illegal characters
                Arguments.of(":path", "/\0\r\n\u007F"),
                Arguments.of(":authority", "\u0001\u001F\u007F"),

                // Embedded in middle of value
                Arguments.of(":path", "/path/with\u0000embedded/nul"),
                Arguments.of(":authority", "exam\u000Bple.com"),
                Arguments.of(":scheme", "ht\u001Ftp"),
                Arguments.of(":method", "GE\u007FT")
        );
    }

    private static void verifyAllPseudoHeadersPresent(Http2Headers headers) {
        for (PseudoHeaderName pseudoName : PseudoHeaderName.values()) {
            assertNotNull(headers.get(pseudoName.value()));
        }
    }

    static void verifyPseudoHeadersFirst(Http2Headers headers) {
        CharSequence lastNonPseudoName = null;
        for (Entry<CharSequence, CharSequence> entry: headers) {
            if (entry.getKey().length() == 0 || entry.getKey().charAt(0) != ':') {
                lastNonPseudoName = entry.getKey();
            } else if (lastNonPseudoName != null) {
                fail("All pseudo headers must be first in iteration. Pseudo header " + entry.getKey() +
                        " is after a non pseudo header " + lastNonPseudoName);
            }
        }
    }

    private static Http2Headers newHeaders() {
        Http2Headers headers = new DefaultHttp2Headers();
        headers.add(of("name1"), of("value1"), of("value2"));
        headers.method(of("POST"));
        headers.add(of("2name"), of("value3"));
        headers.path(of("/index.html"));
        headers.status(of("200"));
        headers.authority(of("netty.io"));
        headers.add(of("name3"), of("value4"));
        headers.scheme(of("https"));
        headers.add(of(":protocol"), of("websocket"));
        return headers;
    }
}
