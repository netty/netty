/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.buffer;

import static org.junit.Assert.*;

import java.io.EOFException;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ChannelBufferStreamTest {

    @Test
    public void testAll() throws Exception {
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();

        try {
            new ChannelBufferOutputStream(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        ChannelBufferOutputStream out = new ChannelBufferOutputStream(buf);
        assertSame(buf, out.buffer());
        out.writeBoolean(true);
        out.writeBoolean(false);
        out.writeByte(42);
        out.writeByte(224);
        out.writeBytes("Hello, World!");
        out.writeChars("Hello, World");
        out.writeChar('!');
        out.writeDouble(42.0);
        out.writeFloat(42.0f);
        out.writeInt(42);
        out.writeLong(42);
        out.writeShort(42);
        out.writeShort(49152);
        out.writeUTF("Hello, World!");
        out.writeBytes("The first line\r\r\n");
        out.write(new byte[0]);
        out.write(new byte[] { 1, 2, 3, 4 });
        out.write(new byte[] { 1, 3, 3, 4 }, 0, 0);
        out.close();

        try {
            new ChannelBufferInputStream(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            new ChannelBufferInputStream(null, 0);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            new ChannelBufferInputStream(buf, -1);
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            new ChannelBufferInputStream(buf, buf.capacity() + 1);
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        ChannelBufferInputStream in = new ChannelBufferInputStream(buf);

        assertTrue(in.markSupported());
        in.mark(Integer.MAX_VALUE);

        assertEquals(buf.writerIndex(), in.skip(Long.MAX_VALUE));
        assertFalse(buf.readable());

        in.reset();
        assertEquals(0, buf.readerIndex());

        assertEquals(4, in.skip(4));
        assertEquals(4, buf.readerIndex());
        in.reset();

        assertTrue(in.readBoolean());
        assertFalse(in.readBoolean());
        assertEquals(42, in.readByte());
        assertEquals(224, in.readUnsignedByte());

        byte[] tmp = new byte[13];
        in.readFully(tmp);
        assertEquals("Hello, World!", new String(tmp, "ISO-8859-1"));

        assertEquals('H', in.readChar());
        assertEquals('e', in.readChar());
        assertEquals('l', in.readChar());
        assertEquals('l', in.readChar());
        assertEquals('o', in.readChar());
        assertEquals(',', in.readChar());
        assertEquals(' ', in.readChar());
        assertEquals('W', in.readChar());
        assertEquals('o', in.readChar());
        assertEquals('r', in.readChar());
        assertEquals('l', in.readChar());
        assertEquals('d', in.readChar());
        assertEquals('!', in.readChar());

        assertEquals(42.0, in.readDouble(), 0.0);
        assertEquals(42.0f, in.readFloat(), 0.0);
        assertEquals(42, in.readInt());
        assertEquals(42, in.readLong());
        assertEquals(42, in.readShort());
        assertEquals(49152, in.readUnsignedShort());

        assertEquals("Hello, World!", in.readUTF());
        assertEquals("The first line", in.readLine());

        assertEquals(4, in.read(tmp));
        assertEquals(1, tmp[0]);
        assertEquals(2, tmp[1]);
        assertEquals(3, tmp[2]);
        assertEquals(4, tmp[3]);

        assertEquals(-1, in.read());
        assertEquals(-1, in.read(tmp));

        try {
            in.readByte();
            fail();
        } catch (EOFException e) {
            // Expected
        }

        try {
            in.readFully(tmp, 0, -1);
            fail();
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        try {
            in.readFully(tmp);
            fail();
        } catch (EOFException e) {
            // Expected
        }

        in.close();

        assertEquals(buf.readerIndex(), in.readBytes());
    }
}
