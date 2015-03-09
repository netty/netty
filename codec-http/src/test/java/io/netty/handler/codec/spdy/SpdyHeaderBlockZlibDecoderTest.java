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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpdyHeaderBlockZlibDecoderTest {

    // zlib header indicating 32K window size fastest deflate algorithm with SPDY dictionary
    private static final byte[] zlibHeader = {0x78, 0x3f, (byte) 0xe3, (byte) 0xc6, (byte) 0xa7, (byte) 0xc2};
    private static final byte[] zlibSyncFlush = {0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff};

    private static final int maxHeaderSize = 8192;

    private static final String name = "name";
    private static final String value = "value";
    private static final byte[] nameBytes = name.getBytes();
    private static final byte[] valueBytes = value.getBytes();

    private SpdyHeaderBlockZlibDecoder decoder;
    private SpdyHeadersFrame frame;

    @Before
    public void setUp() {
        decoder = new SpdyHeaderBlockZlibDecoder(SpdyVersion.SPDY_3_1, maxHeaderSize);
        frame = new DefaultSpdyHeadersFrame(1);
    }

    @After
    public void tearDown() {
        decoder.end();
    }

    @Test
    public void testHeaderBlock() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(37));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x15); // little-endian length (21)
        headerBlock.writeByte(0x00); // little-endian length (21)
        headerBlock.writeByte(0xea); // one's compliment of length
        headerBlock.writeByte(0xff); // one's compliment of length
        headerBlock.writeInt(1); // number of Name/Value pairs
        headerBlock.writeInt(4); // length of name
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5); // length of value
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeBytes(zlibSyncFlush);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertEquals(1, frame.headers().names().size());
        assertTrue(frame.headers().contains(name));
        assertEquals(1, frame.headers().getAll(name).size());
        assertEquals(value, frame.headers().get(name));
    }

    @Test
    public void testHeaderBlockMultipleDecodes() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(37));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x15); // little-endian length (21)
        headerBlock.writeByte(0x00); // little-endian length (21)
        headerBlock.writeByte(0xea); // one's compliment of length
        headerBlock.writeByte(0xff); // one's compliment of length
        headerBlock.writeInt(1); // number of Name/Value pairs
        headerBlock.writeInt(4); // length of name
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5); // length of value
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeBytes(zlibSyncFlush);

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
    }

    @Test
    public void testLargeHeaderName() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(8220));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x0c); // little-endian length (8204)
        headerBlock.writeByte(0x20); // little-endian length (8204)
        headerBlock.writeByte(0xf3); // one's compliment of length
        headerBlock.writeByte(0xdf); // one's compliment of length
        headerBlock.writeInt(1); // number of Name/Value pairs
        headerBlock.writeInt(8192); // length of name
        for (int i = 0; i < 8192; i++) {
            headerBlock.writeByte('n');
        }
        headerBlock.writeInt(0); // length of value
        headerBlock.writeBytes(zlibSyncFlush);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertFalse(frame.isTruncated());
        assertEquals(1, frame.headers().names().size());
    }

    @Test
    public void testLargeHeaderValue() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(8220));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x0c); // little-endian length (8204)
        headerBlock.writeByte(0x20); // little-endian length (8204)
        headerBlock.writeByte(0xf3); // one's compliment of length
        headerBlock.writeByte(0xdf); // one's compliment of length
        headerBlock.writeInt(1); // number of Name/Value pairs
        headerBlock.writeInt(1); // length of name
        headerBlock.writeByte('n');
        headerBlock.writeInt(8191); // length of value
        for (int i = 0; i < 8191; i++) {
            headerBlock.writeByte('v');
        }
        headerBlock.writeBytes(zlibSyncFlush);
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
        decoder.endHeaderBlock(frame);

        assertFalse(headerBlock.isReadable());
        assertFalse(frame.isInvalid());
        assertFalse(frame.isTruncated());
        assertEquals(1, frame.headers().names().size());
        assertEquals(8191, frame.headers().get("n").length());
    }

    @Test(expected = SpdyProtocolException.class)
    public void testHeaderBlockExtraData() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(37));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x15); // little-endian length (21)
        headerBlock.writeByte(0x00); // little-endian length (21)
        headerBlock.writeByte(0xea); // one's compliment of length
        headerBlock.writeByte(0xff); // one's compliment of length
        headerBlock.writeInt(1); // number of Name/Value pairs
        headerBlock.writeInt(4); // length of name
        headerBlock.writeBytes(nameBytes);
        headerBlock.writeInt(5); // length of value
        headerBlock.writeBytes(valueBytes);
        headerBlock.writeByte(0x19); // adler-32 checksum
        headerBlock.writeByte(0xa5); // adler-32 checksum
        headerBlock.writeByte(0x03); // adler-32 checksum
        headerBlock.writeByte(0xc9); // adler-32 checksum
        headerBlock.writeByte(0); // Data following zlib stream
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
    }

    @Test(expected = SpdyProtocolException.class)
    public void testHeaderBlockInvalidDictionary() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(7));
        headerBlock.writeByte(0x78);
        headerBlock.writeByte(0x3f);
        headerBlock.writeByte(0x01); // Unknown dictionary
        headerBlock.writeByte(0x02); // Unknown dictionary
        headerBlock.writeByte(0x03); // Unknown dictionary
        headerBlock.writeByte(0x04); // Unknown dictionary
        headerBlock.writeByte(0); // Non-compressed block
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
    }

    @Test(expected = SpdyProtocolException.class)
    public void testHeaderBlockInvalidDeflateBlock() throws Exception {
        ByteBuf headerBlock = ReferenceCountUtil.releaseLater(Unpooled.buffer(11));
        headerBlock.writeBytes(zlibHeader);
        headerBlock.writeByte(0); // Non-compressed block
        headerBlock.writeByte(0x00); // little-endian length (0)
        headerBlock.writeByte(0x00); // little-endian length (0)
        headerBlock.writeByte(0x00); // invalid one's compliment
        headerBlock.writeByte(0x00); // invalid one's compliment
        decoder.decode(ByteBufAllocator.DEFAULT, headerBlock, frame);
    }
}
