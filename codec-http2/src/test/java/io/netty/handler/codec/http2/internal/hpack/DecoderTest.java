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
package io.netty.handler.codec.http2.internal.hpack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.Before;
import org.junit.Test;

import static io.netty.handler.codec.http2.Http2TestUtil.newTestDecoder;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.AsciiString.of;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DecoderTest {

    private static final int MAX_HEADER_LIST_SIZE = 8192;
    private static final int MAX_HEADER_TABLE_SIZE = 4096;

    private Decoder decoder;
    private Http2Headers mockHeaders;

    private static String hex(String s) {
        return Hex.encodeHexString(s.getBytes());
    }

    private void decode(String encoded) throws Http2Exception {
        byte[] b = Hex.decodeHex(encoded.toCharArray());
        ByteBuf in = Unpooled.wrappedBuffer(b);
        try {
            decoder.decode(0, in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Before
    public void setUp() throws Http2Exception {
        decoder = new Decoder();
        mockHeaders = mock(Http2Headers.class);
    }

    @Test
    public void testLiteralHuffmanEncodedWithEmptyNameAndValue() throws Http2Exception {
        byte[] input = {0, (byte) 0x80, 0};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(0, in, mockHeaders);
            verify(mockHeaders, times(1)).add(EMPTY_STRING, EMPTY_STRING);
        } finally {
            in.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralHuffmanEncodedWithPaddingGreaterThan7Throws() throws Http2Exception {
        byte[] input = {0, (byte) 0x81, -1};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(0, in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralHuffmanEncodedWithDecodingEOSThrows() throws Http2Exception {
        byte[] input = {0, (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(0, in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralHuffmanEncodedWithPaddingNotCorrespondingToMSBThrows() throws Http2Exception {
        byte[] input = {0, (byte) 0x81, 0};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(0, in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testIncompleteIndex() throws Http2Exception, Http2Exception {
        byte[] compressed = Hex.decodeHex("FFF0".toCharArray());
        ByteBuf in = Unpooled.wrappedBuffer(compressed);
        try {
            decoder.decode(0, in, mockHeaders);
            assertEquals(1, in.readableBytes());
            decoder.decode(0, in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void testUnusedIndex() throws Http2Exception {
        // Index 0 is not used
        decode("80");
    }

    @Test(expected = Http2Exception.class)
    public void testIllegalIndex() throws Http2Exception {
        // Index larger than the header table
        decode("FF00");
    }

    @Test(expected = Http2Exception.class)
    public void testInsidiousIndex() throws Http2Exception {
        // Insidious index so the last shift causes sign overflow
        decode("FF8080808007");
    }

    @Test
    public void testDynamicTableSizeUpdate() throws Http2Exception {
        decode("20");
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("3FE11F");
        assertEquals(4096, decoder.getMaxHeaderTableSize());
    }

    @Test
    public void testDynamicTableSizeUpdateRequired() throws Http2Exception {
        decoder.setMaxHeaderTableSize(32);
        decode("3F00");
        assertEquals(31, decoder.getMaxHeaderTableSize());
    }

    @Test(expected = Http2Exception.class)
    public void testIllegalDynamicTableSizeUpdate() throws Http2Exception {
        // max header table size = MAX_HEADER_TABLE_SIZE + 1
        decode("3FE21F");
    }

    @Test(expected = Http2Exception.class)
    public void testInsidiousMaxDynamicTableSize() throws Http2Exception {
        decoder.setMaxHeaderTableSize(MAX_VALUE);
        // max header table size sign overflow
        decode("3FE1FFFFFF07");
    }

    @Test
    public void testMaxValidDynamicTableSize() throws Http2Exception {
        decoder.setMaxHeaderTableSize(MAX_VALUE);
        String baseValue = "3FE1FFFFFF0";
        for (int i = 0; i < 7; ++i) {
            decode(baseValue + i);
        }
    }

    @Test
    public void testReduceMaxDynamicTableSize() throws Http2Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("2081");
    }

    @Test(expected = Http2Exception.class)
    public void testTooLargeDynamicTableSizeUpdate() throws Http2Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("21"); // encoder max header table size not small enough
    }

    @Test(expected = Http2Exception.class)
    public void testMissingDynamicTableSizeUpdate() throws Http2Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("81");
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            sb.append('a');
        }
        String value = sb.toString();
        sb = new StringBuilder();
        sb.append("417F811F");
        for (int i = 0; i < 4096; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
        verify(mockHeaders).add(of(":authority"), of(value));
        verifyNoMoreInteractions(mockHeaders);
        reset(mockHeaders);

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockHeaders, times(2)).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralWithIncrementalIndexingWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("4004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
    }

    @Test
    public void testLiteralWithoutIndexingWithEmptyName() throws Http2Exception {
        decode("000005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralWithoutIndexingWithLargeName() throws Http2Exception {
        // Ignore header name that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("007F817F");
        for (int i = 0; i < 16384; i++) {
            sb.append("61"); // 'a'
        }
        sb.append("00");
        decode(sb.toString());
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralWithoutIndexingWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("0004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
    }

    @Test
    public void testLiteralNeverIndexedWithEmptyName() throws Http2Exception {
        decode("100005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralNeverIndexedWithLargeName() throws Http2Exception {
        // Ignore header name that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("107F817F");
        for (int i = 0; i < 16384; i++) {
            sb.append("61"); // 'a'
        }
        sb.append("00");
        decode(sb.toString());
    }

    @Test(expected = Http2Exception.class)
    public void testLiteralNeverIndexedWithLargeValue() throws Http2Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("1004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
    }
}
