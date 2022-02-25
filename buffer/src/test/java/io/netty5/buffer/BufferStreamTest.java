/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.netty5.util.internal.EmptyArrays.EMPTY_BYTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests channel buffer streams
 */
public class BufferStreamTest {

    @Test
    public void testAll() throws Exception {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(65536);

        try {
            new BufferOutputStream(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        BufferOutputStream out = new BufferOutputStream(buf);
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
            new BufferInputStream(null);
            fail();
        } catch (NullPointerException e) {
            // Expected
        }

        try (BufferInputStream in = new BufferInputStream(buf.send())) {
            assertTrue(in.markSupported());
            in.mark(Integer.MAX_VALUE);

            assertEquals(in.buffer().writerOffset(), in.skip(Long.MAX_VALUE));
            assertEquals(0, in.buffer().readableBytes());

            in.reset();
            assertEquals(0, in.buffer().readerOffset());

            assertEquals(4, in.skip(4));
            assertEquals(4, in.buffer().readerOffset());
            in.reset();

            assertTrue(in.readBoolean());
            assertFalse(in.readBoolean());
            assertEquals(42, in.readByte());
            assertEquals(224, in.readUnsignedByte());

            byte[] tmp = new byte[13];
            in.readFully(tmp);
            assertEquals("Hello, World!", new String(tmp, StandardCharsets.ISO_8859_1));

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
            assertEquals(in.buffer().readerOffset(), in.readBytes());
        }
    }

    @Test
    public void testReadLine() throws Exception {
        Charset utf8 = StandardCharsets.UTF_8;
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(256);
        BufferInputStream in = new BufferInputStream(buf.send());

        String s = in.readLine();
        assertNull(s);
        in.close();

        Buffer buf2 = BufferAllocator.onHeapUnpooled().allocate(256);
        int charCount = 7; //total chars in the string below without new line characters
        byte[] abc = "\na\n\nb\r\nc\nd\ne".getBytes(utf8);
        buf2.writeBytes(abc);

        BufferInputStream in2 = new BufferInputStream(buf2.send());
        in2.mark(charCount);
        assertEquals("", in2.readLine());
        assertEquals("a", in2.readLine());
        assertEquals("", in2.readLine());
        assertEquals("b", in2.readLine());
        assertEquals("c", in2.readLine());
        assertEquals("d", in2.readLine());
        assertEquals("e", in2.readLine());
        assertNull(in.readLine());

        in2.reset();
        int count = 0;
        while (in2.readLine() != null) {
            ++count;
            if (count > charCount) {
                fail("readLine() should have returned null");
            }
        }
        assertEquals(charCount, count);
        in2.close();
    }

    @Test
    public void testRead() throws Exception {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(16);
        buf.writeBytes(new byte[]{1, 2, 3, 4, 5, 6});

        try (BufferInputStream in = new BufferInputStream(buf.send())) {
            assertEquals(1, in.read());
            assertEquals(2, in.read());
            assertEquals(3, in.read());
            assertEquals(4, in.read());
            assertEquals(5, in.read());
            assertEquals(6, in.read());
            assertEquals(-1, in.read());
        }
    }

    @Test
    public void testReadLineLengthRespected2() throws Exception {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(16);
        buf.writeBytes(new byte[] { 'A', 'B', '\n', 'C', 'E', 'F'});

        try (BufferInputStream in2 = new BufferInputStream(buf.send())) {
            assertEquals("AB", in2.readLine());
            assertEquals("CEF", in2.readLine());
            assertNull(in2.readLine());
        }
    }

    @Test
    public void testReadByteLengthRespected() throws Exception {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(16);

        try (BufferInputStream in = new BufferInputStream(buf.send())) {
            assertThrows(EOFException.class, in::readByte);
        }
    }

    @Test
    public void writesToClosedStreamMustThrow() throws IOException {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(16);
        BufferOutputStream stream = new BufferOutputStream(buf);
        stream.close();
        buf.close();
        assertThrows(IOException.class, () -> stream.write(0));
        assertThrows(IOException.class, () -> stream.write(new byte[1]));
        assertThrows(IOException.class, () -> stream.write(new byte[1], 0, 1));
        assertThrows(IOException.class, () -> stream.write(EMPTY_BYTES));
        assertThrows(IOException.class, () -> stream.write(new byte[1], 0, 0));
        assertThrows(IOException.class, () -> stream.writeBoolean(true));
        assertThrows(IOException.class, () -> stream.writeByte(0));
        assertThrows(IOException.class, () -> stream.writeBytes("0"));
        assertThrows(IOException.class, () -> stream.writeBytes(""));
        assertThrows(IOException.class, () -> stream.writeChar(0));
        assertThrows(IOException.class, () -> stream.writeChars("0"));
        assertThrows(IOException.class, () -> stream.writeChars(""));
        assertThrows(IOException.class, () -> stream.writeFloat(0));
        assertThrows(IOException.class, () -> stream.writeInt(0));
        assertThrows(IOException.class, () -> stream.writeShort(0));
        assertThrows(IOException.class, () -> stream.writeLong(0));
        assertThrows(IOException.class, () -> stream.writeDouble(0));
        assertThrows(IOException.class, () -> stream.writeUTF("0"));
        assertThrows(IOException.class, () -> stream.writeUTF(""));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void readsToClosedStreamMustThrow() throws IOException {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(16);
        BufferInputStream stream = new BufferInputStream(buf.send());
        stream.close();
        assertThrows(IOException.class, () -> stream.read());
        assertThrows(IOException.class, () -> stream.read(new byte[1]));
        assertThrows(IOException.class, () -> stream.read(new byte[1], 0, 1));
        assertThrows(IOException.class, () -> stream.readFully(new byte[1]));
        assertThrows(IOException.class, () -> stream.readFully(new byte[1], 0, 1));
        assertThrows(IOException.class, () -> stream.readNBytes(0));
        assertThrows(IOException.class, () -> stream.read(EMPTY_BYTES));
        assertThrows(IOException.class, () -> stream.read(new byte[1], 0, 0));
        assertThrows(IOException.class, () -> stream.readBoolean());
        assertThrows(IOException.class, () -> stream.readByte());
        assertThrows(IOException.class, () -> stream.readUnsignedByte());
        assertThrows(IOException.class, () -> stream.readAllBytes());
        assertThrows(IOException.class, () -> stream.readNBytes(1));
        assertThrows(IOException.class, () -> stream.readChar());
        assertThrows(IOException.class, () -> stream.readFloat());
        assertThrows(IOException.class, () -> stream.readInt());
        assertThrows(IOException.class, () -> stream.readShort());
        assertThrows(IOException.class, () -> stream.readUnsignedShort());
        assertThrows(IOException.class, () -> stream.readLong());
        assertThrows(IOException.class, () -> stream.readDouble());
        assertThrows(IOException.class, () -> stream.readUTF());
        stream.readBytes(); // Query method... not actually reading
    }
}
