/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
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

    @Test
    public void testEmptyHeaderBlock() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.EMPTY_BUFFER;
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testZeroNameValuePairs() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(4);
        headerBlock.writeInt(0);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testNegativeNameValuePairs() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(4);
        headerBlock.writeInt(-1);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testOneNameValuePair() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(21);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testMissingNameLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(4);
        headerBlock.writeInt(1);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testZeroNameLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(0);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testNegativeNameLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(-1);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testMissingName() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(8);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testIllegalNameOnlyNull() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(18);
        headerBlock.writeInt(1);
        headerBlock.writeInt(1);
        headerBlock.writeByte(0);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testMissingValueLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(12);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testZeroValueLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(0);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals("", frame.headers().get(name));
    }

    @Test
    public void testNegativeValueLength() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(-1);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testMissingValue() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(16);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testIllegalValueOnlyNull() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(17);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(1);
        headerBlock.writeByte(0);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testIllegalValueStartsWithNull() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(6);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testIllegalValueEndsWithNull() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(6);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testMultipleValues() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(27);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(11);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(2, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().getAll(name).get(0));
        assertEquals(value, frame.headers().getAll(name).get(1));
    }

    @Test
    public void testMultipleValuesEndsWithNull() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(28);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(12);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testIllegalValueMultipleNulls() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(28);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(12);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        headerBlock.writeByte(0);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testMissingNextNameValuePair() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(21);
        headerBlock.writeInt(2);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testMultipleNames() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(38);
        headerBlock.writeInt(2);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testExtraData() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(22);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0);
        decoder.decode(headerBlock, frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testMultipleDecodes() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(21);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);

        int readableBytes = headerBlock.readableBytes();
        for (int i = 0; i < readableBytes; i++) {
            ChannelBuffer headerBlockSegment = headerBlock.slice(i, 1);
            decoder.decode(headerBlockSegment, frame);
            assertFalse(headerBlockSegment.readable());
        }
        decoder.endHeaderBlock(frame);

        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testContinueAfterInvalidHeaders() throws Exception {
        ChannelBuffer numHeaders = ChannelBuffers.buffer(4);
        numHeaders.writeInt(1);

        ChannelBuffer nameBlock = ChannelBuffers.buffer(8);
        nameBlock.writeInt(4);
        nameBlock.writeBytes(nameBytes);

        ChannelBuffer valueBlock = ChannelBuffers.buffer(9);
        valueBlock.writeInt(5);
        valueBlock.writeBytes(valueBytes);

        decoder.decode(numHeaders, frame);
        decoder.decode(nameBlock, frame);
        frame.setInvalid();
        decoder.decode(valueBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(numHeaders.readable());
        assertFalse(nameBlock.readable());
        assertFalse(valueBlock.readable());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testTruncatedHeaderName() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(maxHeaderSize + 18);
        headerBlock.writeInt(1);
        headerBlock.writeInt(maxHeaderSize + 1);
        for (int i = 0; i < maxHeaderSize + 1; i++) {
            headerBlock.writeByte('a');
        }
        headerBlock.writeInt(5);
        headerBlock.writeBytes(valueBytes);
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isTruncated());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }

    @Test
    public void testTruncatedHeaderValue() throws Exception {
        ChannelBuffer headerBlock = ChannelBuffers.buffer(maxHeaderSize + 13);
        headerBlock.writeInt(1);
        headerBlock.writeInt(4);
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(13);
        for (int i = 0; i < maxHeaderSize - 3; i++) {
            headerBlock.writeByte('a');
        }
        decoder.decode(headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.readable());
        assertTrue(frame.isTruncated());
        assertFalse(frame.isInvalid());
        assertEquals(0, frame.headers().names().size());
    }
}
