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

import static io.netty.handler.codec.http2.hpack.HpackUtil.ISO_8859_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class DecoderTest {

    private static final int MAX_HEADER_SIZE = 8192;
    private static final int MAX_HEADER_TABLE_SIZE = 4096;

    private Decoder decoder;
    private HeaderListener mockListener;

    private static String hex(String s) {
        return Hex.encodeHexString(s.getBytes());
    }

    private static byte[] getBytes(String s) {
        return s.getBytes(ISO_8859_1);
    }

    private void decode(String encoded) throws IOException {
        byte[] b = Hex.decodeHex(encoded.toCharArray());
        decoder.decode(new ByteArrayInputStream(b), mockListener);
    }

    @Before
    public void setUp() {
        decoder = new Decoder(MAX_HEADER_SIZE, MAX_HEADER_TABLE_SIZE);
        mockListener = mock(HeaderListener.class);
    }

    @Test
    public void testIncompleteIndex() throws IOException {
        // Verify incomplete indices are unread
        byte[] compressed = Hex.decodeHex("FFF0".toCharArray());
        ByteArrayInputStream in = new ByteArrayInputStream(compressed);
        decoder.decode(in, mockListener);
        assertEquals(1, in.available());
        decoder.decode(in, mockListener);
        assertEquals(1, in.available());
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

    @Test(expected = IOException.class)
    public void testLiteralWithIncrementalIndexingWithEmptyName() throws Exception {
        decode("000005" + hex("value"));
    }

    @Test
    public void testLiteralWithIncrementalIndexingCompleteEviction() throws Exception {
        // Verify indexed host header
        decode("4004" + hex("name") + "05" + hex("value"));
        verify(mockListener).addHeader(getBytes("name"), getBytes("value"), false);
        verifyNoMoreInteractions(mockListener);
        assertFalse(decoder.endHeaderBlock());

        reset(mockListener);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            sb.append("a");
        }
        String value = sb.toString();
        sb = new StringBuilder();
        sb.append("417F811F");
        for (int i = 0; i < 4096; i++) {
            sb.append("61"); // 'a'
        }
        decode(sb.toString());
        verify(mockListener).addHeader(getBytes(":authority"), getBytes(value), false);
        verifyNoMoreInteractions(mockListener);
        assertFalse(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockListener, times(2)).addHeader(getBytes("name"), getBytes("value"), false);
        verifyNoMoreInteractions(mockListener);
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
        verifyNoMoreInteractions(mockListener);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockListener, times(2)).addHeader(getBytes("name"), getBytes("value"), false);
        verifyNoMoreInteractions(mockListener);
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
        verifyNoMoreInteractions(mockListener);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify next header is inserted at index 62
        decode("4004" + hex("name") + "05" + hex("value") + "BE");
        verify(mockListener, times(2)).addHeader(getBytes("name"), getBytes("value"), false);
        verifyNoMoreInteractions(mockListener);
    }

    @Test(expected = IOException.class)
    public void testLiteralWithoutIndexingWithEmptyName() throws Exception {
        decode("000005" + hex("value"));
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
        verifyNoMoreInteractions(mockListener);

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
        verifyNoMoreInteractions(mockListener);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }

    @Test(expected = IOException.class)
    public void testLiteralNeverIndexedWithEmptyName() throws Exception {
        decode("100005" + hex("value"));
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
        verifyNoMoreInteractions(mockListener);

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
        verifyNoMoreInteractions(mockListener);

        // Verify header block is reported as truncated
        assertTrue(decoder.endHeaderBlock());

        // Verify table is unmodified
        decode("BE");
    }
}
