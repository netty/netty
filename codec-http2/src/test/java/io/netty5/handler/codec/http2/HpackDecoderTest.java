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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.http2.headers.DefaultHttp2Headers;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.internal.StringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.HpackDecoder.decodeULE128;
import static io.netty5.handler.codec.http2.Http2HeadersEncoder.NEVER_SENSITIVE;
import static io.netty5.util.AsciiString.EMPTY_STRING;
import static io.netty5.util.AsciiString.of;
import static java.lang.Integer.MAX_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class HpackDecoderTest {

    private HpackDecoder hpackDecoder;
    private Http2Headers mockHeaders;

    private static String hex(String s) {
        return StringUtil.toHexString(s.getBytes());
    }

    private void decode(String encoded) throws Http2Exception {
        byte[] input = StringUtil.decodeHexDump(encoded);
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            hpackDecoder.decode(0, in, mockHeaders, true);
        }
    }

    @BeforeEach
    public void setUp() {
        hpackDecoder = new HpackDecoder(8192);
        mockHeaders = mock(Http2Headers.class);
    }

    @Test
    public void testDecodeULE128IntMax() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x07};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertEquals(MAX_VALUE, decodeULE128(in, 0));
        }
    }

    @Test
    public void testDecodeULE128IntOverflow1() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x07};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            final int readerIndex = in.readerOffset();
            assertThrows(Http2Exception.class, () -> decodeULE128(in, 1));
            assertEquals(readerIndex, in.readerOffset());
        }
    }

    @Test
    public void testDecodeULE128IntOverflow2() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x08};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            final int readerIndex = in.readerOffset();
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decodeULE128(in, 0);
                }
            });
            assertEquals(readerIndex, in.readerOffset());
        }
    }

    @Test
    public void testDecodeULE128LongMax() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0x7F};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertEquals(Long.MAX_VALUE, decodeULE128(in, 0L));
        }
    }

    @Test
    public void testDecodeULE128LongOverflow1() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0xFF};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            final int readerIndex = in.readerOffset();
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decodeULE128(in, 0L);
                }
            });
            assertEquals(readerIndex, in.readerOffset());
        }
    }

    @Test
    public void testDecodeULE128LongOverflow2() throws Http2Exception {
        byte[] input = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                        (byte) 0xFF, (byte) 0x7F};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            final int readerIndex = in.readerOffset();
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    decodeULE128(in, 1L);
                }
            });
            assertEquals(readerIndex, in.readerOffset());
        }
    }

    @Test
    public void testSetTableSizeWithMaxUnsigned32BitValueSucceeds() throws Http2Exception {
        byte[] input = {(byte) 0x3F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0E};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            final long expectedHeaderSize = 4026531870L; // based on the input above
            hpackDecoder.setMaxHeaderTableSize(expectedHeaderSize);
            hpackDecoder.decode(0, in, mockHeaders, true);
            assertEquals(expectedHeaderSize, hpackDecoder.getMaxHeaderTableSize());
        }
    }

    @Test
    public void testSetTableSizeOverLimitFails() throws Http2Exception {
        byte[] input = {(byte) 0x3F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0E};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            hpackDecoder.setMaxHeaderTableSize(4026531870L - 1); // based on the input above ... 1 less than is above.
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void testLiteralHuffmanEncodedWithEmptyNameAndValue() throws Http2Exception {
        byte[] input = {0, (byte) 0x80, 0};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            hpackDecoder.decode(0, in, mockHeaders, true);
            verify(mockHeaders, times(1)).add(EMPTY_STRING, EMPTY_STRING);
        }
    }

    @Test
    public void testLiteralHuffmanEncodedWithPaddingGreaterThan7Throws() throws Http2Exception {
        byte[] input = {0, (byte) 0x81, -1};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void testLiteralHuffmanEncodedWithDecodingEOSThrows() throws Http2Exception {
        byte[] input = {0, (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void testLiteralHuffmanEncodedWithPaddingNotCorrespondingToMSBThrows() throws Http2Exception {
        byte[] input = {0, (byte) 0x81, 0};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void testIncompleteIndex() throws Http2Exception {
        byte[] input = StringUtil.decodeHexDump("FFF0");
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertEquals(2, in.readableBytes());
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void testUnusedIndex() throws Http2Exception {
        // Index 0 is not used
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("80");
            }
        });
    }

    @Test
    public void testIllegalIndex() throws Http2Exception {
        // Index larger than the header table
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("FF00");
            }
        });
    }

    @Test
    public void testInsidiousIndex() throws Http2Exception {
        // Insidious index so the last shift causes sign overflow
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("FF8080808007");
            }
        });
    }

    @Test
    public void testDynamicTableSizeUpdate() throws Http2Exception {
        decode("20");
        assertEquals(0, hpackDecoder.getMaxHeaderTableSize());
        decode("3FE11F");
        assertEquals(4096, hpackDecoder.getMaxHeaderTableSize());
    }

    @Test
    public void testDynamicTableSizeUpdateRequired() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(32);
        decode("3F00");
        assertEquals(31, hpackDecoder.getMaxHeaderTableSize());
    }

    @Test
    public void testIllegalDynamicTableSizeUpdate() throws Http2Exception {
        // max header table size = MAX_HEADER_TABLE_SIZE + 1
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("3FE21F");
            }
        });
    }

    @Test
    public void testInsidiousMaxDynamicTableSize() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(MAX_VALUE);
        // max header table size sign overflow
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("3FE1FFFFFF07");
            }
        });
    }

    @Test
    public void testMaxValidDynamicTableSize() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(MAX_VALUE);
        String baseValue = "3FE1FFFFFF0";
        for (int i = 0; i < 7; ++i) {
            decode(baseValue + i);
        }
    }

    @Test
    public void testReduceMaxDynamicTableSize() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(0);
        assertEquals(0, hpackDecoder.getMaxHeaderTableSize());
        decode("2081");
    }

    @Test
    public void testTooLargeDynamicTableSizeUpdate() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(0);
        assertEquals(0, hpackDecoder.getMaxHeaderTableSize());
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("21"); // encoder max header table size not small enough
            }
        });
    }

    @Test
    public void testMissingDynamicTableSizeUpdate() throws Http2Exception {
        hpackDecoder.setMaxHeaderTableSize(0);
        assertEquals(0, hpackDecoder.getMaxHeaderTableSize());
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode("81");
            }
        });
    }

    @Test
    public void testLiteralWithIncrementalIndexingWithEmptyName() throws Http2Exception {
        decode("400005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test
    public void testLiteralWithIncrementalIndexingCompleteEviction() throws Http2Exception {
        // Verify indexed host header
        decode("4004" + hex("name") + "05" + hex("value"));
        verify(mockHeaders).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);

        reset(mockHeaders);
        String value = "a".repeat(4096);
        StringBuilder sb = new StringBuilder();
        sb.append("417F811F");
        sb.append("61".repeat(4096)); // 'a'
        decode(sb.toString());
        verify(mockHeaders).contains(of(":authority"));
        verify(mockHeaders).add(of(":authority"), of(value));
        verifyNoMoreInteractions(mockHeaders);
        reset(mockHeaders);

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockHeaders, times(2)).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
    }

    @Test
    public void testLiteralWithIncrementalIndexingWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        final StringBuilder sb = new StringBuilder();
        sb.append("4004");
        sb.append(hex("name"));
        sb.append("7F813F");
        sb.append("61".repeat(8192)); // 'a'
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode(sb.toString());
            }
        });
    }

    @Test
    public void testLiteralWithoutIndexingWithEmptyName() throws Http2Exception {
        decode("000005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test
    public void testLiteralWithoutIndexingWithLargeName() throws Http2Exception {
        // Ignore header name that exceeds max header size
        final StringBuilder sb = new StringBuilder();
        sb.append("007F817F");
        sb.append("61".repeat(16384)); // 'a'
        sb.append("00");
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode(sb.toString());
            }
        });
    }

    @Test
    public void testLiteralWithoutIndexingWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        final StringBuilder sb = new StringBuilder();
        sb.append("0004");
        sb.append(hex("name"));
        sb.append("7F813F");
        sb.append("61".repeat(8192)); // 'a'
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode(sb.toString());
            }
        });
    }

    @Test
    public void testLiteralNeverIndexedWithEmptyName() throws Http2Exception {
        decode("100005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test
    public void testLiteralNeverIndexedWithLargeName() throws Http2Exception {
        // Ignore header name that exceeds max header size
        final StringBuilder sb = new StringBuilder();
        sb.append("107F817F");
        sb.append("61".repeat(16384)); // 'a'
        sb.append("00");
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode(sb.toString());
            }
        });
    }

    @Test
    public void testLiteralNeverIndexedWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        final StringBuilder sb = new StringBuilder();
        sb.append("1004");
        sb.append(hex("name"));
        sb.append("7F813F");
        sb.append("61".repeat(8192)); // 'a'
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                decode(sb.toString());
            }
        });
    }

    @Test
    public void testDecodeLargerThanMaxHeaderListSizeUpdatesDynamicTable() throws Http2Exception {
        try (Buffer in = onHeapAllocator().allocate(300)) {
            hpackDecoder.setMaxHeaderListSize(200);
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            // encode headers that are slightly larger than maxHeaderListSize
            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add("test_1", "1");
            toEncode.add("test_2", "2");
            toEncode.add("long", String.format("%0100d", 0).replace('0', 'A'));
            toEncode.add("test_3", "3");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            // decode the headers, we should get an exception
            final Http2Headers decoded = Http2Headers.newHeaders();
            assertThrows(Http2Exception.HeaderListSizeException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });

            // but the dynamic table should have been updated, so that later blocks
            // can refer to earlier headers
            in.resetOffsets();
            // 0x80, "indexed header field representation"
            // index 62, the first (most recent) dynamic table entry
            in.writeByte((byte) (0x80 | 62));
            Http2Headers decoded2 = Http2Headers.newHeaders();
            hpackDecoder.decode(1, in, decoded2, true);

            Http2Headers golden = Http2Headers.newHeaders();
            golden.add("test_3", "3");
            assertEquals(golden, decoded2);
        }
    }

    @Test
    public void testDecodeCountsNamesOnlyOnce() throws Http2Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            hpackDecoder.setMaxHeaderListSize(3500);
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            // encode headers that are slightly larger than maxHeaderListSize
            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add(String.format("%03000d", 0).replace('0', 'f'), "value");
            toEncode.add("accept", "value");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            Http2Headers decoded = Http2Headers.newHeaders();
            hpackDecoder.decode(1, in, decoded, true);
            assertEquals(2, decoded.size());
        }
    }

    @Test
    public void testAccountForHeaderOverhead() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(100)) {
            String headerName = "12345";
            String headerValue = "56789";
            long headerSize = headerName.length() + headerValue.length();
            hpackDecoder.setMaxHeaderListSize(headerSize);
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add(headerName, headerValue);
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            // SETTINGS_MAX_HEADER_LIST_SIZE is big enough for the header to fit...
            assertThat(hpackDecoder.getMaxHeaderListSize(), is(greaterThanOrEqualTo(headerSize)));

            // ... but decode should fail because we add some overhead for each header entry
            assertThrows(Http2Exception.HeaderListSizeException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });
        }
    }

    @Test
    public void testIncompleteHeaderFieldRepresentation() throws Http2Exception {
        // Incomplete Literal Header Field with Incremental Indexing
        byte[] input = {(byte) 0x40};
        try (Buffer in = onHeapAllocator().copyOf(input)) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(0, in, mockHeaders, true);
                }
            });
        }
    }

    @Test
    public void unknownPseudoHeader() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders(false);
            toEncode.add(":test", "1");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            assertThrows(Http2Exception.StreamException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });
        }
    }

    @Test
    public void disableHeaderValidation() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders(false);
            toEncode.add(":test", "1");
            toEncode.add(":status", "200");
            toEncode.add(":method", "GET");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            Http2Headers decoded = Http2Headers.newHeaders(false);

            hpackDecoder.decode(1, in, decoded, false);

            assertThat(decoded.valuesIterator(":test").next().toString(), is("1"));
            assertThat(decoded.status().toString(), is("200"));
            assertThat(decoded.method().toString(), is("GET"));
        }
    }

    @Test
    public void requestPseudoHeaderInResponse() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add(":status", "200");
            toEncode.add(":method", "GET");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            assertThrows(Http2Exception.StreamException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });
        }
    }

    @Test
    public void responsePseudoHeaderInRequest() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add(":method", "GET");
            toEncode.add(":status", "200");
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            assertThrows(Http2Exception.StreamException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });
        }
    }

    @Test
    public void pseudoHeaderAfterRegularHeader() throws Exception {
        try (Buffer in = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = new DefaultHttp2Headers(2, false, false, false) {
                @Override
                public Iterator<Entry<CharSequence, CharSequence>> iterator() {
                    Entry<CharSequence, CharSequence> regular = Map.entry("test", "1");
                    Entry<CharSequence, CharSequence> pseudo = Map.entry(":method", "GET");
                    return List.of(regular, pseudo).iterator();
                }
            };
            hpackEncoder.encodeHeaders(1, in, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            assertThrows(Http2Exception.StreamException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in, decoded, true);
                }
            });
        }
    }

    @Test
    public void failedValidationDoesntCorruptHpack() throws Exception {
        try (Buffer in1 = onHeapAllocator().allocate(200);
             Buffer in2 = onHeapAllocator().allocate(200)) {
            HpackEncoder hpackEncoder = new HpackEncoder(true);

            Http2Headers toEncode = Http2Headers.newHeaders();
            toEncode.add(":method", "GET");
            toEncode.add(":status", "200");
            toEncode.add("foo", "bar");
            hpackEncoder.encodeHeaders(1, in1, toEncode, NEVER_SENSITIVE);

            final Http2Headers decoded = Http2Headers.newHeaders();

            Http2Exception.StreamException expected =
                    assertThrows(Http2Exception.StreamException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackDecoder.decode(1, in1, decoded, true);
                }
            });
            assertEquals(1, expected.streamId());

            // Do it again, this time without validation, to make sure the HPACK state is still sane.
            decoded.clear();
            hpackEncoder.encodeHeaders(1, in2, toEncode, NEVER_SENSITIVE);
            hpackDecoder.decode(1, in2, decoded, false);

            assertEquals(3, decoded.size());
            assertEquals("GET", decoded.method().toString());
            assertEquals("200", decoded.status().toString());
            assertEquals("bar", decoded.get("foo").toString());
        }
    }
}
