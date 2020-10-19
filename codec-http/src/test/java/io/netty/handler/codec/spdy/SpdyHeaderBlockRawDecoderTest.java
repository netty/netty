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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpdyHeaderBlockRawDecoderTest {

    private static final int maxHeaderSize = 16;

    private static final String name = "name";
    private static final String value = "value";
    private static final byte[] nameBytes = name.getBytes();
    private static final byte[] valueBytes = value.getBytes();

    private SpdyHeaderBlockRawDecoder decoder;
    private SpdyHeadersFrame frame;

    @Before
    public void setUp() {
        decoder = new SpdyHeaderBlockRawDecoder(SpdyVersion.SPDY_3_1, maxHeaderSize);
        frame = new DefaultSpdyHeadersFrame(1);
    }

    @After
    public void tearDown() {
        decoder.end();
    }

    @Test
    public void testEmptyHeaderBlock() throws Exception {
        ByteBuf headerBlock = Unpooled.EMPTY_BUFFER;
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testZeroNameValuePairs() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(4);
        headerBlock.writeInt(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testNegativeNameValuePairs() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(4);
        headerBlock.writeInt(-1);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testOneNameValuePair() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(21);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testMissingNameLength() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(4);
        headerBlock.writeInt(1);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testZeroNameLength() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testNegativeNameLength() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(-1);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testMissingName() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testIllegalNameOnlyNull() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(18);
        headerBlock.writeInt(1);
        headerBlock.writeInt(1);
        headerBlock.writeByte(0);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testMissingValueLength() throws Exception {
        ByteBuf headerBlock =  Unpooled.buffer(12);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testZeroValueLength() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals("", frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testNegativeValueLength() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(-1);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testMissingValue() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testIllegalValueOnlyNull() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(17);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(1);
        headerBlock.writeByte(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testIllegalValueStartsWithNull() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(6);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testIllegalValueEndsWithNull() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(6);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testMultipleValues() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(27);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(11);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(2, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().getAll(name).get(0));
        assertEquals(value, frame.headers().getAll(name).get(1));
        headerBlock.release();
    }

    @Test
    public void testMultipleValuesEndsWithNull() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(28);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(12);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testIllegalValueMultipleNulls() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(28);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(12);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testMissingNextNameValuePair() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(21);
        headerBlock.writeInt(2);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testMultipleNames() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(38);
        headerBlock.writeInt(2);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testExtraData() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testMultipleDecodes() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(21);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);

        int readableBytes = headerBlock.readableBytes();
        for (int i = 0; i < readableBytes; i++) {
            ByteBuf headerBlockSegment = headerBlock.slice(i, 1);
            decoder.decode(ByteBufAllocator.DEFAULT, headerBlockSegment, frame);
            assertFalse(headerBlockSegment.isReadable());
        }
        decoder.endHeaderBlock(frame);

        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        headerBlock.release();
    }

    @Test
    public void testContinueAfterInvalidHeaders() throws Exception {
        ByteBuf numHeaders = Unpooled.buffer(4);
        numHeaders.writeInt(1);

        ByteBuf nameBlock = Unpooled.buffer(8);
        nameBlock.writeInt(4);
        nameBlock.writeBytes(nameBytes);

        ByteBuf valueBlock = Unpooled.buffer(9);
        valueBlock.writeInt(5);
        valueBlock.writeBytes(valueBytes);

        decoder.decode(ByteBufAllocator.DEFAULT, numHeaders, frame);
        decoder.decode(ByteBufAllocator.DEFAULT, nameBlock, frame);
        frame.setInvalid();
        decoder.decode(ByteBufAllocator.DEFAULT, valueBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(numHeaders.isReadable());
        assertFalse(nameBlock.isReadable());
        assertFalse(valueBlock.isReadable());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
        numHeaders.release();
        nameBlock.release();
        valueBlock.release();
    }

    @Test
    public void testTruncatedHeaderName() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(maxHeaderSize + 18);
        headerBlock.writeInt(1);
        headerBlock.writeInt(maxHeaderSize + 1);
        for (int i = 0; i < maxHeaderSize + 1; i++) {
            headerBlock.writeByte('a');
        }
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isTruncated());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }

    @Test
    public void testTruncatedHeaderValue() throws Exception {
        ByteBuf headerBlock = Unpooled.buffer(maxHeaderSize + 13);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(13);
        for (int i = 0; i < maxHeaderSize - 3; i++) {
            headerBlock.writeByte('a');
        }
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertTrue(frame.isTruncated());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
        headerBlock.release();
    }
}
