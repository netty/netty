/*
 * Copyright 2014 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.util.AsciiString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty5.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static io.netty5.handler.codec.http2.Http2TestUtil.randomBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link DefaultHttp2HeadersDecoder}.
 */
public class DefaultHttp2HeadersDecoderTest {

    private DefaultHttp2HeadersDecoder decoder;

    @BeforeEach
    public void setup() {
        decoder = new DefaultHttp2HeadersDecoder(false);
    }

    @Test
    public void decodeShouldSucceed() throws Exception {
        try (Buffer buf = encode(b(":method"), b("GET"), b("akey"), b("avalue"), randomBytes(), randomBytes())) {
            Http2Headers headers = decoder.decodeHeaders(0, buf);
            assertEquals(3, headers.size());
            assertEquals("GET", headers.method().toString());
            assertEquals("avalue", headers.get(new AsciiString("akey")).toString());
        }
    }

    @Test
    public void testExceedHeaderSize() throws Exception {
        final int maxListSize = 100;
        decoder.configuration().maxHeaderListSize(maxListSize, maxListSize);
        try (Buffer buf = encode(randomBytes(maxListSize), randomBytes(1))) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decoder.decodeHeaders(0, buf);
                }
            });
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MAX_HEADER_LIST_SIZE);
        try (Buffer buf = encode(b(":method"), b("GET"))) {
            final int streamId = 1;
            Http2Exception.HeaderListSizeException e =
                    assertThrows(Http2Exception.HeaderListSizeException.class, new Executable() {
                        @Override
                        public void execute() throws Throwable {
                            decoder.decodeHeaders(streamId, buf);
                        }
                    });
            assertEquals(streamId, e.streamId());
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeButLessThanGoAwayWithInitialDecoderSettings() throws Exception {
        try (Buffer buf = encode(b(":method"), b("GET"), b("test_header"),
                b(String.format("%09000d", 0).replace('0', 'A')))) {
            final int streamId = 1;
            Http2Exception.HeaderListSizeException e = assertThrows(Http2Exception.HeaderListSizeException.class,
                    new Executable() {
                        @Override
                        public void execute() throws Throwable {
                            decoder.decodeHeaders(streamId, buf);
                        }
                    });
            assertEquals(streamId, e.streamId());
        }
    }

    @Test
    public void decodeLargerThanHeaderListSizeGoAway() throws Exception {
        decoder.maxHeaderListSize(MIN_HEADER_LIST_SIZE, MIN_HEADER_LIST_SIZE);
        try (Buffer buf = encode(b(":method"), b("GET"))) {
            final int streamId = 1;
            Http2Exception e = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decoder.decodeHeaders(streamId, buf);
                }
            });
            assertEquals(Http2Error.PROTOCOL_ERROR, e.error());
        }
    }

    @Test
    public void duplicatePseudoHeadersMustFailValidation() throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
        verifyValidationFails(decoder, encode(b(":authority"), b("abc"), b(":authority"), b("def")));
    }

    @Test
    public void decodingTrailersTeHeaderMustNotFailValidation() throws Exception {
        // The TE header is expressly allowed to have the value "trailers".
        try (Buffer buf = encode(b(":method"), b("GET"), b("te"), b("trailers"))) {
            Http2Headers headers = decoder.decodeHeaders(1, buf); // This must not throw.
            assertThat(headers.get(HttpHeaderNames.TE)).isEqualToIgnoringCase(HttpHeaderValues.TRAILERS);
        }
    }

    @Test
    public void decodingConnectionRelatedHeadersMustFailValidation() throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true, true);
        // Standard connection related headers
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("keep-alive"), b("timeout=5")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"),
                b("connection"), b("keep-alive"), b("keep-alive"), b("timeout=5")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("transfer-encoding"), b("chunked")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"),
                b("connection"), b("transfer-encoding"), b("transfer-encoding"), b("chunked")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("upgrade"), b("foo/2")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"),
                b("connection"), b("upgrade"), b("upgrade"), b("foo/2")));
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("connection"), b("close")));

        // Non-standard connection related headers:
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("proxy-connection"), b("keep-alive")));

        // Only "trailers" is allowed for the TE header:
        verifyValidationFails(decoder, encode(b(":method"), b("GET"), b("te"), b("compress")));
    }

    public static List<Integer> illegalFirstChar() {
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < 0x21; i++) {
            list.add(i);
        }
        list.add(0x7F);
        return list;
    }

    @ParameterizedTest
    @MethodSource("illegalFirstChar")
    void decodingInvalidHeaderValueMustFailValidationIfFirstCharIsIllegal(int illegalFirstChar)throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true, true);
        verifyValidationFails(decoder, encode(b(":method"), b("GET"),
                b("test_header"), new byte[]{ (byte) illegalFirstChar, (byte) 'a' }));
    }

    public static List<Integer> illegalNotFirstChar() {
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < 0x21; i++) {
            if (i == ' ' || i == '\t') {
                continue; // Space and horizontal tab are only illegal as first chars.
            }
            list.add(i);
        }
        list.add(0x7F);
        return list;
    }

    @ParameterizedTest
    @MethodSource("illegalNotFirstChar")
    void decodingInvalidHeaderValueMustFailValidationIfANotFirstCharIsIllegal(int illegalSecondChar) throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true, true);
        verifyValidationFails(decoder, encode(b(":method"), b("GET"),
                b("test_header"), new byte[]{ (byte) 'a', (byte) illegalSecondChar }));
    }

    @Test
    public void headerValuesAllowSpaceAfterFirstCharacter() throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
        try (Buffer buf = encode(b(":method"), b("GET"), b("test_header"), b("a b"))) {
            Http2Headers headers = decoder.decodeHeaders(1, buf); // This must not throw.
            assertThat(headers.get("test_header")).isEqualToIgnoringCase("a b");
        }
    }

    @Test
    public void headerValuesAllowHorzontalTabAfterFirstCharacter() throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
        try (Buffer buf = encode(b(":method"), b("GET"), b("test_header"), b("a\tb"))) {
            Http2Headers headers = decoder.decodeHeaders(1, buf); // This must not throw.
            assertThat(headers.get("test_header")).isEqualToIgnoringCase("a\tb");
        }
    }

    public static List<Integer> validObsText() {
        ArrayList<Integer> list = new ArrayList<Integer>();
        for (int i = 0x80; i <= 0xFF; i++) {
            list.add(i);
        }
        return list;
    }

    @ParameterizedTest
    @MethodSource("validObsText")
    void headerValuesAllowObsTextInFirstChar(int i) throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
        byte[] bytes = {(byte) i, 'a'};
        try (Buffer buf = encode(b(":method"), b("GET"), b("test_header"), bytes)) {
            Http2Headers headers = decoder.decodeHeaders(1, buf); // This must not throw.
            assertThat(headers.get("test_header")).isEqualTo(new AsciiString(bytes));
        }
    }

    @ParameterizedTest
    @MethodSource("validObsText")
    void headerValuesAllowObsTextInNonFirstChar(int i) throws Exception {
        final DefaultHttp2HeadersDecoder decoder = new DefaultHttp2HeadersDecoder(true);
        byte[] bytes = {(byte) 'a', (byte) i};
        try (Buffer buf = encode(b(":method"), b("GET"), b("test_header"), bytes)) {
            Http2Headers headers = decoder.decodeHeaders(1, buf); // This must not throw.
            assertThat(headers.get("test_header")).isEqualTo(new AsciiString(bytes));
        }
    }

    private static void verifyValidationFails(final DefaultHttp2HeadersDecoder decoder, final Buffer buf) {
        try (buf) {
            Http2Exception e = assertThrows(Http2Exception.class, () -> decoder.decodeHeaders(1, buf));
            assertEquals(Http2Error.PROTOCOL_ERROR, e.error());
        }
    }

    private static byte[] b(String string) {
        return string.getBytes(UTF_8);
    }

    private static Buffer encode(byte[]... entries) throws Exception {
        HpackEncoder hpackEncoder = newTestEncoder();
        Buffer out = onHeapAllocator().allocate(256);
        Http2Headers http2Headers = Http2Headers.newHeaders(false);
        for (int ix = 0; ix < entries.length;) {
            http2Headers.add(new AsciiString(entries[ix++], false), new AsciiString(entries[ix++], false));
        }
        hpackEncoder.encodeHeaders(3 /* randomly chosen */, out, http2Headers, NEVER_SENSITIVE);
        return out;
    }
}
