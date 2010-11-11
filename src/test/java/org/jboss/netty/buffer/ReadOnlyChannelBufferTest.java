/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import static org.easymock.EasyMock.*;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import org.junit.Test;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class ReadOnlyChannelBufferTest {

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullInConstructor() {
        new ReadOnlyChannelBuffer(null);
    }

    @Test
    public void testUnmodifiableBuffer() {
        assertTrue(ChannelBuffers.unmodifiableBuffer(ChannelBuffers.buffer(1)) instanceof ReadOnlyChannelBuffer);
    }

    @Test
    public void testUnwrap() {
        ChannelBuffer buf = ChannelBuffers.buffer(1);
        assertSame(buf, ((WrappedChannelBuffer) ChannelBuffers.unmodifiableBuffer(buf)).unwrap());
    }

    @Test
    public void shouldHaveSameByteOrder() {
        ChannelBuffer buf = ChannelBuffers.buffer(ChannelBuffers.LITTLE_ENDIAN, 1);
        assertSame(ChannelBuffers.LITTLE_ENDIAN, ChannelBuffers.unmodifiableBuffer(buf).order());
    }

    @Test
    public void shouldReturnReadOnlyDerivedBuffer() {
        ChannelBuffer buf = ChannelBuffers.unmodifiableBuffer(ChannelBuffers.buffer(1));
        assertTrue(buf.duplicate() instanceof ReadOnlyChannelBuffer);
        assertTrue(buf.slice() instanceof ReadOnlyChannelBuffer);
        assertTrue(buf.slice(0, 1) instanceof ReadOnlyChannelBuffer);
        assertTrue(buf.duplicate() instanceof ReadOnlyChannelBuffer);
    }

    @Test
    public void shouldReturnWritableCopy() {
        ChannelBuffer buf = ChannelBuffers.unmodifiableBuffer(ChannelBuffers.buffer(1));
        assertFalse(buf.copy() instanceof ReadOnlyChannelBuffer);
    }

    @Test
    public void shouldForwardReadCallsBlindly() throws Exception {
        ChannelBuffer buf = createStrictMock(ChannelBuffer.class);
        expect(buf.readerIndex()).andReturn(0).anyTimes();
        expect(buf.writerIndex()).andReturn(0).anyTimes();
        expect(buf.capacity()).andReturn(0).anyTimes();

        expect(buf.getBytes(1, (GatheringByteChannel) null, 2)).andReturn(3);
        buf.getBytes(4, (OutputStream) null, 5);
        buf.getBytes(6, (byte[]) null, 7, 8);
        buf.getBytes(9, (ChannelBuffer) null, 10, 11);
        buf.getBytes(12, (ByteBuffer) null);
        expect(buf.getByte(13)).andReturn(Byte.valueOf((byte) 14));
        expect(buf.getShort(15)).andReturn(Short.valueOf((short) 16));
        expect(buf.getUnsignedMedium(17)).andReturn(18);
        expect(buf.getInt(19)).andReturn(20);
        expect(buf.getLong(21)).andReturn(22L);

        ByteBuffer bb = ByteBuffer.allocate(100);
        ByteBuffer[] bbs = new ByteBuffer[] { ByteBuffer.allocate(101), ByteBuffer.allocate(102) };

        expect(buf.toByteBuffer(23, 24)).andReturn(bb);
        expect(buf.toByteBuffers(25, 26)).andReturn(bbs);
        expect(buf.capacity()).andReturn(27);

        replay(buf);

        ChannelBuffer roBuf = unmodifiableBuffer(buf);
        assertEquals(3, roBuf.getBytes(1, (GatheringByteChannel) null, 2));
        roBuf.getBytes(4, (OutputStream) null, 5);
        roBuf.getBytes(6, (byte[]) null, 7, 8);
        roBuf.getBytes(9, (ChannelBuffer) null, 10, 11);
        roBuf.getBytes(12, (ByteBuffer) null);
        assertEquals((byte) 14, roBuf.getByte(13));
        assertEquals((short) 16, roBuf.getShort(15));
        assertEquals(18, roBuf.getUnsignedMedium(17));
        assertEquals(20, roBuf.getInt(19));
        assertEquals(22L, roBuf.getLong(21));

        ByteBuffer roBB = roBuf.toByteBuffer(23, 24);
        assertEquals(100, roBB.capacity());
        assertTrue(roBB.isReadOnly());

        ByteBuffer[] roBBs = roBuf.toByteBuffers(25, 26);
        assertEquals(2, roBBs.length);
        assertEquals(101, roBBs[0].capacity());
        assertTrue(roBBs[0].isReadOnly());
        assertEquals(102, roBBs[1].capacity());
        assertTrue(roBBs[1].isReadOnly());
        assertEquals(27, roBuf.capacity());

        verify(buf);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectDiscardReadBytes() {
        unmodifiableBuffer(EMPTY_BUFFER).discardReadBytes();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetByte() {
        unmodifiableBuffer(EMPTY_BUFFER).setByte(0, (byte) 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetShort() {
        unmodifiableBuffer(EMPTY_BUFFER).setShort(0, (short) 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetMedium() {
        unmodifiableBuffer(EMPTY_BUFFER).setMedium(0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetInt() {
        unmodifiableBuffer(EMPTY_BUFFER).setInt(0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetLong() {
        unmodifiableBuffer(EMPTY_BUFFER).setLong(0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetBytes1() throws IOException {
        unmodifiableBuffer(EMPTY_BUFFER).setBytes(0, (InputStream) null, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetBytes2() throws IOException {
        unmodifiableBuffer(EMPTY_BUFFER).setBytes(0, (ScatteringByteChannel) null, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetBytes3() {
        unmodifiableBuffer(EMPTY_BUFFER).setBytes(0, (byte[]) null, 0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetBytes4() {
        unmodifiableBuffer(EMPTY_BUFFER).setBytes(0, (ChannelBuffer) null, 0, 0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldRejectSetBytes5() {
        unmodifiableBuffer(EMPTY_BUFFER).setBytes(0, (ByteBuffer) null);
    }
}
