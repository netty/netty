/*
 * Copyright 2012 The Netty Project
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

package org.jboss.netty.buffer;

import org.jboss.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.experimental.theories.suppliers.TestedOn;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.Arrays.asList;
import static org.hamcrest.core.IsNot.not;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

@RunWith(Theories.class)
public class EmptyChannelBufferTest {

    @After
    public void assertInvariants() {
        assertEquals(0, b.readerIndex());
        assertEquals(0, b.writerIndex());
        assertEquals(0, b.writableBytes());
        assertEquals(0, b.readableBytes());
        assertFalse(b.writable());
        assertFalse(b.readable());
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public static final ScatteringByteChannel SINGLE_BYTE_CHANNEL = new ScatteringByteChannel() {
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            dsts[0].put((byte) 0);
            return 1;
        }

        public long read(ByteBuffer[] dsts) throws IOException {
            dsts[0].put((byte) 0);
            return 1;

        }

        public int read(ByteBuffer dst) throws IOException {
            dst.put((byte) 0);
            return 1;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() throws IOException {

        }
    };
    private static final ScatteringByteChannel EMPTY_BYTE_CHANNEL = new ScatteringByteChannel() {
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return 0;
        }

        public long read(ByteBuffer[] dsts) throws IOException {
            return 0;

        }

        public int read(ByteBuffer dst) throws IOException {
            return 0;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() throws IOException {

        }
    };

    private static final GatheringByteChannel BYTE_CHANNEL_SINK = new GatheringByteChannel() {
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            int n = 0;
            for (ByteBuffer src : srcs) {
                n += write(src);
            }
            return n;
        }

        public long write(ByteBuffer[] srcs) throws IOException {
            return write(srcs, 0, 0);
        }

        public int write(ByteBuffer src) throws IOException {
            int n = src.remaining();
            src.get(new byte[src.remaining()]);
            return n;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() throws IOException {

        }
    };

    public static final byte[] SINGLE_BYTE = new byte[]{0};

    public static final ChannelBufferIndexFinder POSITIVE_INDEX_FINDER = new ChannelBufferIndexFinder() {
        public boolean find(ChannelBuffer buffer, int guessedIndex) {
            return true;
        }
    };

    public static final ChannelBuffer UNWRITTEN_BUFFER = buffer(1);

    final ChannelBuffer b = EMPTY_BUFFER;

    @Test
    public void testDiscardReadBytes() {
        b.discardReadBytes();
    }

    @Test
    public void testClear() {
        b.clear();
    }

    @Test
    public void testWriteBytes() throws IOException {
        b.writeBytes(new byte[]{});
        b.writeBytes(new byte[]{}, 0, 0);
        b.writeBytes(ByteBuffer.wrap(new byte[]{}));
        b.writeBytes(buffer(0));
        b.writeBytes(buffer(0), 0);
        b.writeBytes(buffer(0), 0, 0);
        b.writeBytes(new ChannelBufferInputStream(buffer(0)), 0);
        b.writeBytes(EMPTY_BYTE_CHANNEL, 0);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds1() throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(SINGLE_BYTE);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds3() throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(ByteBuffer.wrap(SINGLE_BYTE));
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds4() throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(wrappedBuffer(SINGLE_BYTE));
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds2(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(SINGLE_BYTE, 0, length);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds5(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(wrappedBuffer(SINGLE_BYTE), length);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds6(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(wrappedBuffer(SINGLE_BYTE), 0, length);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds7(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(new ChannelBufferInputStream(wrappedBuffer(SINGLE_BYTE)), length);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds8(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(SINGLE_BYTE_CHANNEL, length);
    }

    @Theory
    public void testWriteBytesIndexOutOfBounds9(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeBytes(SINGLE_BYTE, 0, length);
    }

    @Test
    public void testWriteZero() {
        b.writeZero(0);
    }

    @Theory
    public void testWriteZeroOutOfBounds(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeZero(length);
    }

    @Theory
    public void testSetZero(@TestedOn(ints = {-1, 1}) int offset) {
        b.setZero(0, 0);
        b.setZero(1, 0);
        b.setZero(-1, 0);
    }

    @Theory
    public void testSetZeroIllegalArgument() {
        thrown.expect(IllegalArgumentException.class);
        b.setZero(-1, -1);
    }

    @Theory
    public void testSetZeroOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setZero(offset, 1);
    }

    @Test
    public void testSetBytes() {
        b.setBytes(0, wrappedBuffer(new byte[]{}), 0);
        b.setBytes(0, wrappedBuffer(new byte[]{}), 0, 0);
        b.setBytes(0, ByteBuffer.wrap(new byte[]{}));
        b.setBytes(0, buffer(0));
        b.setBytes(0, buffer(0));
        b.setBytes(0, buffer(0), 0);
        b.setBytes(0, buffer(0), 0, 0);
    }

    @Theory
    public void testSetBytesIndexOutOfBounds1(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setBytes(offset, SINGLE_BYTE);
    }

    @Theory
    public void testSetBytesIndexOutOfBounds5(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setBytes(offset, ByteBuffer.wrap(SINGLE_BYTE));
    }

    @Theory
    public void testSetBytesIndexOutOfBounds2(@TestedOn(ints = {-1, 0, 1}) int offset,
                                              @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.setBytes(offset, SINGLE_BYTE, 0, length);
    }

    @Theory
    public void testSetBytesIndexOutOfBounds3(@TestedOn(ints = {-1, 0, 1}) int offset,
                                              @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.setBytes(offset, wrappedBuffer(SINGLE_BYTE), length);
    }

    @Theory
    public void testSetBytesIndexOutOfBounds4(@TestedOn(ints = {-1, 0, 1}) int offset,
                                              @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.setBytes(offset, wrappedBuffer(SINGLE_BYTE), 0, length);
    }

    @Theory
    public void testSetByteOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                       @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setByte(offset, value);
    }

    @Theory
    public void testSetCharOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                       @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setChar(offset, value);
    }

    @Theory
    public void testSetShortOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                        @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setShort(offset, value);
    }

    @Theory
    public void testSetMediumOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setMedium(offset, value);
    }

    @Theory
    public void testSetIntOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                      @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setInt(offset, value);
    }

    @Theory
    public void testSetLongOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                       @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setLong(offset, value);
    }

    @Theory
    public void testSetFloatOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                        @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setFloat(offset, value);
    }

    @Theory
    public void testSetDoubleOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.setDouble(offset, value);
    }

    @Theory
    public void testGetByteOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getByte(offset);
    }

    @Theory
    public void testGetCharOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getChar(offset);
    }

    @Theory
    public void testGetShortOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getShort(offset);
    }

    @Theory
    public void testGetMediumOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getMedium(offset);
    }

    @Theory
    public void testGetIntOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getInt(offset);
    }

    @Theory
    public void testGetLongOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getLong(offset);
    }

    @Theory
    public void testGetUnsignedByteOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getUnsignedByte(offset);
    }

    @Theory
    public void testGetUnsignedShortOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getUnsignedShort(offset);
    }

    @Theory
    public void testGetUnsignedMediumOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getUnsignedMedium(offset);
    }

    @Theory
    public void testGetUnsignedIntOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getUnsignedInt(offset);
    }

    @Theory
    public void testGetDoubleOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getDouble(offset);
    }

    @Theory
    public void testGetFloatOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getFloat(offset);
    }

    @Test
    public void testCopy() {
        assertEquals(EMPTY_BUFFER, b.copy());
        assertEquals(EMPTY_BUFFER, b.copy(0, 0));
    }

    @Theory
    public void testCopyOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                    @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.copy(offset, length);
    }

    @Test
    public void testReadByteOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readByte();
    }

    @Test
    public void testReadCharOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readChar();
    }

    @Test
    public void testReadDoubleOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readDouble();
    }

    @Test
    public void testReadFloatOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readFloat();
    }

    @Test
    public void testReadIntOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readInt();
    }

    @Test
    public void testReadLongOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readLong();
    }

    @Test
    public void testReadMediumOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readMedium();
    }

    @Test
    public void testReadShortOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readShort();
    }

    @Test
    public void testReadUnsignedByteOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readUnsignedByte();
    }

    @Test
    public void testReadUnsignedIntOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readUnsignedInt();
    }

    @Test
    public void testReadUnsignedMediumOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readUnsignedMedium();
    }

    @Test
    public void testReadUnsignedShortOutOfBounds() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readUnsignedShort();
    }

    @Theory
    public void testWriteByteOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeByte(value);
    }

    @Theory
    public void testWriteCharOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeChar(value);
    }

    @Theory
    public void testWriteDoubleOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeDouble(value);
    }

    @Theory
    public void testWriteFloatOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeFloat(value);
    }

    @Theory
    public void testWriteIntOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeInt(value);
    }

    @Theory
    public void testWriteLongOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeLong(value);
    }

    @Theory
    public void testWriteMediumOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeMedium(value);
    }

    @Theory
    public void testWriteShortOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int value) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writeShort(value);
    }


    @Test
    public void testReadBytes() throws IOException {
        assertEquals(EMPTY_BUFFER, b.readBytes(0));
        b.readBytes(new byte[]{});
        b.readBytes(new byte[]{}, 0, 0);
        b.readBytes(ByteBuffer.allocate(0));
        b.readBytes(buffer(0));
        b.readBytes(buffer(0), 0);
        b.readBytes(buffer(0), 0, 0);
        b.readBytes(BYTE_CHANNEL_SINK, 0);
    }

    @Theory
    public void testReadBytesOutOfBounds1(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(length);
    }


    @Theory
    public void testReadBytesOutOfBounds2() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(new byte[1]);
    }

    @Theory
    public void testReadBytesOutOfBounds4() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(ByteBuffer.allocate(1));
    }

    @Theory
    public void testReadBytesOutOfBounds5() {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(buffer(1));
    }

    @Theory
    public void testReadBytesOutOfBounds3(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(new byte[1], 0, length);
    }

    @Theory
    public void testReadBytesOutOfBounds6(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(buffer(1), length);
    }

    @Theory
    public void testReadBytesOutOfBounds7(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(buffer(1), 0, length);
    }

    @Theory
    public void testReadBytesOutOfBounds8(@TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readBytes(BYTE_CHANNEL_SINK, length);
    }

    @Test
    public void testIndexes() {
        assertEquals(0, b.readerIndex());
        assertEquals(0, b.writerIndex());
        assertEquals(0, b.readableBytes());
        assertEquals(0, b.writableBytes());
        assertFalse(b.readable());
        assertFalse(b.writable());

        b.markReaderIndex();
        b.markWriterIndex();
        b.resetReaderIndex();
        b.resetWriterIndex();

        b.writerIndex(0);
        b.readerIndex(0);

        b.setIndex(0, 0);
    }

    @Theory
    public void testWriterIndexOutOfBounds(@TestedOn(ints = {-1, 1}) int index) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.writerIndex(index);
    }

    @Theory
    public void testReaderIndexOutOfBounds(@TestedOn(ints = {-1, 1}) int index) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readerIndex(index);
    }

    @Theory
    public void testIndexesOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int writer,
                                       @TestedOn(ints = {-1, 0, 1}) int reader) {

        assumeThat(asList(writer, reader), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.setIndex(reader, writer);
    }

    @Theory
    public void testIndexOf(@TestedOn(ints = {-1, 0, 1}) int from,
                            @TestedOn(ints = {-1, 0, 1}) int to,
                            @TestedOn(ints = {-1, 0, 1}) int value) {
        assertEquals(-1, b.indexOf(from, to, (byte) value));
    }

    @Test
    public void testEquals() {
        assertTrue(b.equals(EMPTY_BUFFER));
        assertTrue(b.equals(ChannelBuffers.buffer(1)));
        assertFalse(b.equals(wrappedBuffer(SINGLE_BYTE)));
    }

    @Test
    public void testFactory() {
        assertEquals(HeapChannelBufferFactory.getInstance(BIG_ENDIAN), b.factory());
    }

    @Test
    public void testEnsureWritableBytes() {
        b.ensureWritableBytes(0);

        try {
            b.ensureWritableBytes(1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void testSlice() {
        assertEquals(EMPTY_BUFFER, b.slice());
        assertEquals(EMPTY_BUFFER, b.slice(0, 0));
    }

    @Theory
    public void testSliceOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                     @TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.slice(offset, length);
    }

    @Test
    public void testReadSlice() {
        assertEquals(EMPTY_BUFFER, b.readSlice(0));
    }

    @Theory
    public void testReadSliceOutOfBounds(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.readSlice(length);
    }

    @Test
    public void testDuplicate() {
        assertEquals(EMPTY_BUFFER, b.duplicate());
    }

    @Theory
    public void testBytesBefore(@TestedOn(ints = {-1, 0, 1}) int value) {
        assertEquals(-1, b.bytesBefore((byte) value));
        assertEquals(-1, b.bytesBefore(POSITIVE_INDEX_FINDER));
        assertEquals(-1, b.bytesBefore(0, (byte) value));
        assertEquals(-1, b.bytesBefore(0, (byte) value));
        assertEquals(-1, b.bytesBefore(0, 0, (byte) value));
        assertEquals(-1, b.bytesBefore(0, 0, POSITIVE_INDEX_FINDER));
    }

    @Theory
    public void testBytesBeforeOutOfBounds1(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.bytesBefore(length, POSITIVE_INDEX_FINDER);
    }

    @Theory
    public void testBytesBeforeOutOfBounds2(@TestedOn(ints = {-1, 0, 1}) int offset,
                                            @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.bytesBefore(offset, length, POSITIVE_INDEX_FINDER);
        b.bytesBefore(offset, length, POSITIVE_INDEX_FINDER);
    }

    @Test
    public void testOrder() {
        assertEquals(ChannelBuffers.BIG_ENDIAN, b.order());
    }

    @Test
    public void testArray() {
        assertEquals(0, b.array().length);
        assertEquals(0, b.arrayOffset());
        assertTrue(b.hasArray());
    }

    @Test
    public void testCapacity() {
        assertEquals(0, b.capacity());
    }

    @Test
    public void testCompareTo() {
        assertEquals(-1, b.compareTo(wrappedBuffer(SINGLE_BYTE)));
        assertEquals(0, b.compareTo(buffer(0)));
        assertEquals(0, b.compareTo(new BigEndianHeapChannelBuffer(0)));
        assertEquals(0, b.compareTo(new BigEndianHeapChannelBuffer(1)));
    }

    @Test
    public void testIsDirect() {
        assertFalse(b.isDirect());
    }

    @Test
    public void testToByteBuffer() {
        assertEquals(ByteBuffer.allocate(0), b.toByteBuffer());
        assertEquals(ByteBuffer.allocate(0), b.toByteBuffer(0, 0));
    }

    @Theory
    public void testToByteBufferOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                            @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.toByteBuffer(offset, length);
    }

    @Test
    public void testToByteBuffers() {
        assertArrayEquals(new ByteBuffer[]{ByteBuffer.allocate(0)}, b.toByteBuffers());
        assertArrayEquals(new ByteBuffer[]{ByteBuffer.allocate(0)}, b.toByteBuffers(0, 0));
    }

    @Theory
    public void testToByteBuffersOutOfBounds(@TestedOn(ints = {-1, 0, 1}) int offset,
                                             @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.toByteBuffers(offset, length);
    }

    @Test
    public void testGetBytes() throws IOException {
        b.getBytes(0, new byte[0]);
        b.getBytes(0, new byte[0], 0, 0);
        b.getBytes(0, ByteBuffer.allocate(0));
        b.getBytes(0, buffer(0));
        b.getBytes(0, buffer(0), 0);
        b.getBytes(0, buffer(0), 0, 0);
        b.getBytes(0, BYTE_CHANNEL_SINK, 0);
        b.getBytes(0, new ChannelBufferOutputStream(buffer(0)), 0);
    }

    @Theory
    public void testGetBytesOutOfBounds1(@TestedOn(ints = {-1, 0, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, new byte[1]);
    }

    @Theory
    public void testGetBytesOutOfBounds2(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, new byte[1], 0, length);
    }

    @Theory
    public void testGetBytesOutOfBounds3(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, buffer(1));
    }

    @Theory
    public void testGetBytesOutOfBounds4(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, buffer(1), length);
    }

    @Theory
    public void testGetBytesOutOfBounds5(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int length) {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, buffer(1), 0, length);
    }

    @Theory
    public void testGetBytesOutOfBounds7(@TestedOn(ints = {-1, 1}) int offset) {
        thrown.expect(IndexOutOfBoundsException.class);
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, ByteBuffer.allocate(1));
    }

    @Theory
    public void testGetBytesOutOfBounds6(@TestedOn(ints = {-1, 0, 1}) int offset,
                                         @TestedOn(ints = {-1, 0, 1}) int length) throws IOException {
        assumeThat(asList(offset, length), not(asList(0, 0)));
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(offset, BYTE_CHANNEL_SINK, length);
    }

    @Theory
    public void testGetBytesOutOfBounds8(@TestedOn(ints = {-1, 0, 1}) int index,
                                         @TestedOn(ints = {-1, 1}) int length) throws IOException {
        thrown.expect(IndexOutOfBoundsException.class);
        b.getBytes(index, new ChannelBufferOutputStream(buffer(1)), length);
    }

    @Test
    public void testSkipBytes() {
        b.skipBytes(0);
    }

    @Theory
    public void testSkipBytesOutOfBounds(@TestedOn(ints = {-1, 1}) int length) {
        thrown.expect(IndexOutOfBoundsException.class);
        b.skipBytes(length);
    }

    @Test
    public void testHashCode() {
        assertEquals(ChannelBuffers.hashCode(UNWRITTEN_BUFFER), b.hashCode());
    }

    @Test
    public void testToString() {
        b.toString();
        assertEquals("", b.toString(CharsetUtil.UTF_8));
        assertEquals("", b.toString(0, 0, CharsetUtil.UTF_8));
    }
}
