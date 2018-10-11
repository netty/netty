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
package io.netty.buffer;

import org.junit.Test;

import java.io.EOFException;
import java.nio.charset.Charset;

import static io.netty.util.internal.EmptyArrays.*;
import static org.junit.Assert.*;

/**
 * Tests channel buffer streams
 */
public class ByteBufStreamTest {

    @Test
    public void testAll() throws Exception {
        ByteBuf buf = Unpooled.buffer(0, 65536);

        try {
            new ByteBufOutputStream(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        ByteBufOutputStream out = new ByteBufOutputStream(buf);
        try {
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
            out.write(EMPTY_BYTES);
            out.write(new byte[]{1, 2, 3, 4});
            out.write(new byte[]{1, 3, 3, 4}, 0, 0);
        } finally {
            out.close();
        }

        try {
            new ByteBufInputStream(null, true);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            new ByteBufInputStream(null, 0, true);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            new ByteBufInputStream(buf.retainedSlice(), -1, true);
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            new ByteBufInputStream(buf.retainedSlice(), buf.capacity() + 1, true);
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        ByteBufInputStream in = new ByteBufInputStream(buf, true);
        try {
            assertTrue(in.markSupported());
            in.mark(Integer.MAX_VALUE);

            assertEquals(buf.writerIndex(), in.skip(Long.MAX_VALUE));
            assertFalse(buf.isReadable());

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
            assertEquals("", in.readLine());

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
        } finally {
            // Ownership was transferred to the ByteBufOutputStream, before we close we must retain the underlying
            // buffer.
            buf.retain();
            in.close();
        }

        assertEquals(buf.readerIndex(), in.readBytes());
        buf.release();
    }

    @Test
    public void testReadLine() throws Exception {
        Charset utf8 = Charset.forName("UTF-8");
        ByteBuf buf = Unpooled.buffer();
        ByteBufInputStream in = new ByteBufInputStream(buf, true);

        String s = in.readLine();
        assertNull(s);

        int charCount = 7; //total chars in the string below without new line characters
        byte[] abc = "\na\n\nb\r\nc\nd\ne".getBytes(utf8);
        buf.writeBytes(abc);
        in.mark(charCount);
        assertEquals("", in.readLine());
        assertEquals("a", in.readLine());
        assertEquals("", in.readLine());
        assertEquals("b", in.readLine());
        assertEquals("c", in.readLine());
        assertEquals("d", in.readLine());
        assertEquals("e", in.readLine());
        assertNull(in.readLine());

        in.reset();
        int count = 0;
        while (in.readLine() != null) {
            ++count;
            if (count > charCount) {
                fail("readLine() should have returned null");
            }
        }
        assertEquals(charCount, count);
        in.close();
    }
}
