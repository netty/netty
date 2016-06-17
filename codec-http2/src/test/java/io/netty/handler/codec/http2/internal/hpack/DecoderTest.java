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
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.AsciiString.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DecoderTest {

    private static final int MAX_HEADER_SIZE = 8192;
    private static final int MAX_HEADER_TABLE_SIZE = 4096;

    private Decoder decoder;
    private Http2Headers mockHeaders;

    private static String hex(String s) {
        return Hex.encodeHexString(s.getBytes());
    }

    private void decode(String encoded) throws IOException {
        byte[] b = Hex.decodeHex(encoded.toCharArray());
        ByteBuf in = Unpooled.wrappedBuffer(b);
        try {
            decoder.decode(in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Before
    public void setUp() {
        decoder = new Decoder(MAX_HEADER_SIZE, MAX_HEADER_TABLE_SIZE, 32);
        mockHeaders = mock(Http2Headers.class);
    }

    @Test
    public void testLiteralHuffmanEncodedWithEmptyNameAndValue() throws IOException {
        byte[] input = {0, (byte) 0x80, 0};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(in, mockHeaders);
            verify(mockHeaders, times(1)).add(EMPTY_STRING, EMPTY_STRING);
        } finally {
            in.release();
        }
    }

    @Test(expected = IOException.class)
    public void testLiteralHuffmanEncodedWithPaddingGreaterThan7Throws() throws IOException {
        byte[] input = {0, (byte) 0x81, -1};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = IOException.class)
    public void testLiteralHuffmanEncodedWithDecodingEOSThrows() throws IOException {
        byte[] input = {0, (byte) 0x84, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test(expected = IOException.class)
    public void testLiteralHuffmanEncodedWithPaddingNotCorrespondingToMSBThrows() throws IOException {
        byte[] input = {0, (byte) 0x81, 0};
        ByteBuf in = Unpooled.wrappedBuffer(input);
        try {
            decoder.decode(in, mockHeaders);
        } finally {
            in.release();
        }
    }

    @Test
    public void testIncompleteIndex() throws IOException {
        // Verify incomplete indices are unread
        byte[] compressed = Hex.decodeHex("FFF0".toCharArray());
        ByteBuf in = Unpooled.wrappedBuffer(compressed);
        try {
            decoder.decode(in, mockHeaders);
            assertEquals(1, in.readableBytes());
            decoder.decode(in, mockHeaders);
            assertEquals(1, in.readableBytes());
        } finally {
            in.release();
        }
    }

    @Test(expected = IOException.class)
    public void testUnusedIndex() throws IOException {
        // Index 0 is not used
        decode("80");
    }

    @Test(expected = IOException.class)
    public void testIllegalIndex() throws IOException {
        // Index larger than the header table
        decode("FF00");
    }

    @Test(expected = IOException.class)
    public void testInsidiousIndex() throws IOException {
        // Insidious index so the last shift causes sign overflow
        decode("FF8080808008");
    }

    @Test
    public void testDynamicTableSizeUpdate() throws Exception {
        decode("20");
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("3FE11F");
        assertEquals(4096, decoder.getMaxHeaderTableSize());
    }

    @Test
    public void testDynamicTableSizeUpdateRequired() throws Exception {
        decoder.setMaxHeaderTableSize(32);
        decode("3F00");
        assertEquals(31, decoder.getMaxHeaderTableSize());
    }

    @Test(expected = IOException.class)
    public void testIllegalDynamicTableSizeUpdate() throws Exception {
        // max header table size = MAX_HEADER_TABLE_SIZE + 1
        decode("3FE21F");
    }

    @Test(expected = IOException.class)
    public void testInsidiousMaxDynamicTableSize() throws IOException {
        // max header table size sign overflow
        decode("3FE1FFFFFF07");
    }

    @Test
    public void testReduceMaxDynamicTableSize() throws Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("2081");
    }

    @Test(expected = IOException.class)
    public void testTooLargeDynamicTableSizeUpdate() throws Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("21"); // encoder max header table size not small enough
    }

    @Test(expected = IOException.class)
    public void testMissingDynamicTableSizeUpdate() throws Exception {
        decoder.setMaxHeaderTableSize(0);
        assertEquals(0, decoder.getMaxHeaderTableSize());
        decode("81");
    }

    @Test
    public void testLiteralWithIncrementalIndexingWithEmptyName() throws Exception {
        decode("400005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test
    public void testLiteralWithIncrementalIndexingCompleteEviction() throws Exception {
        // Verify indexed host header
        decode("4004" + hex("name") + "05" + hex("value"));
        verify(mockHeaders).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
        assertFalse(decoder.endHeaderBlock());

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
        assertFalse(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockHeaders, times(2)).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
    }

    @Test
    public void testLiteralWithIncrementalIndexingWithLargeName() throws Exception {
        // Ignore header name that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("407F817F");
        for (int i = 0; i < 16384; i++) {
            sb.append("61"); // 'a'
        }
        sb.append("00");
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockHeaders, times(2)).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
    }

    @Test
    public void testLiteralWithIncrementalIndexingWithLargeValue() throws Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("4004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockHeaders, times(2)).add(of("name"), of("value"));
        verifyNoMoreInteractions(mockHeaders);
    }

    @Test
    public void testLiteralWithoutIndexingWithEmptyName() throws Exception {
        decode("000005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test(expected = IOException.class)
    public void testLiteralWithoutIndexingWithLargeName() throws Exception {
        // Ignore header name that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("007F817F");
        for (int i = 0; i < 16384; i++) {
            sb.append("61"); // 'a'
        }
        sb.append("00");
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }

    @Test(expected = IOException.class)
    public void testLiteralWithoutIndexingWithLargeValue() throws Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("0004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }

    @Test
    public void testLiteralNeverIndexedWithEmptyName() throws Exception {
        decode("100005" + hex("value"));
        verify(mockHeaders, times(1)).add(EMPTY_STRING, of("value"));
    }

    @Test(expected = IOException.class)
    public void testLiteralNeverIndexedWithLargeName() throws Exception {
        // Ignore header name that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("107F817F");
        for (int i = 0; i < 16384; i++) {
            sb.append("61"); // 'a'
        }
        sb.append("00");
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }

    @Test(expected = IOException.class)
    public void testLiteralNeverIndexedWithLargeValue() throws Exception {
        // Ignore header that exceeds max header size
        StringBuilder sb = new StringBuilder();
        sb.append("1004");
        sb.append(hex("name"));
        sb.append("7F813F");
        for (int i = 0; i < 8192; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
        verifyNoMoreInteractions(mockHeaders);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }
}
