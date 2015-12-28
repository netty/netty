/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ThreadLocalRandom;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Queue;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link ReadableCollection}.
 */
public class ReadableCollectionTest {

    private static byte[] TEST_FILE_DATA = new byte[10];

    private static File TEST_FILE;

    private static ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    @BeforeClass
    public static void setUp() throws IOException {
        ThreadLocalRandom.current().nextBytes(TEST_FILE_DATA);
        TEST_FILE = File.createTempFile("netty-", ".tmp");
        TEST_FILE.deleteOnExit();
        FileOutputStream out = new FileOutputStream(TEST_FILE);
        try {
            out.write(TEST_FILE_DATA);
        } finally {
            out.close();
        }
    }

    private byte[] toBytes(FileRegion region) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            WritableByteChannel channel = Channels.newChannel(out);
            while (region.isTransferable()) {
                region.transferBytesTo(channel, region.transferableBytes());
            }
        } finally {
            region.release();
        }
        return out.toByteArray();
    }

    @Test
    public void testCompose() throws IOException {
        ReadableCollection.Builder builder = ReadableCollection.create(ALLOC, 100);
        for (int i = 0; i < 5; i++) {
            builder.add(Unpooled.wrappedBuffer(Integer.toString(i).getBytes(CharsetUtil.US_ASCII)));
        }
        ReadableCollection rc = builder.build();
        assertTrue(rc.isReadable());
        assertEquals(5, rc.readableBytes());
        ByteBuf buf = rc.unbox();
        assertNotNull(buf);
        assertEquals("01234", buf.toString(CharsetUtil.US_ASCII));
        assertTrue(buf.release());
        builder = ReadableCollection.create(ALLOC, 100);
        builder.add(new DefaultFileRegion(TEST_FILE, 0, TEST_FILE_DATA.length));
        for (int i = 5; i < 10; i++) {
            builder.add(Unpooled.wrappedBuffer(Integer.toString(i).getBytes(CharsetUtil.US_ASCII)));
        }
        rc = builder.build();
        assertNull(rc.unbox());
        assertEquals(5 + TEST_FILE_DATA.length, rc.readableBytes());
        EmbeddedChannel channel = new EmbeddedChannel();
        rc.writeTo(channel, channel.newPromise(), TEST_FILE_DATA.length);
        assertEquals(5, rc.readableBytes());
        channel.flush();
        FileRegion region = (FileRegion) channel.outboundMessages().poll();
        assertArrayEquals(TEST_FILE_DATA, toBytes(region));
        buf = rc.unbox();
        assertNotNull(buf);
        assertEquals("56789", buf.toString(CharsetUtil.US_ASCII));
        assertTrue(buf.release());
    }

    static byte[] toBytes(Queue<Object> queue) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(out);
        for (Object obj; (obj = queue.poll()) != null;) {
            try {
                if (obj instanceof ByteBuf) {
                    ByteBuf buf = (ByteBuf) obj;
                    buf.readBytes(out, buf.readableBytes());
                } else {
                    FileRegion region = (FileRegion) obj;
                    while (region.isTransferable()) {
                        region.transferBytesTo(channel, region.transferableBytes());
                    }
                }
            } finally {
                ReferenceCountUtil.release(obj);
            }
        }
        return out.toByteArray();
    }

    @Test
    public void testWriteTo() throws IOException {
        ReadableCollection.Builder builder = ReadableCollection.create(ALLOC, 100);
        for (int i = 0; i < 5; i++) {
            builder.add(Unpooled.wrappedBuffer(TEST_FILE_DATA)).add(
                    new DefaultFileRegion(TEST_FILE, 0, TEST_FILE_DATA.length));
        }
        ReadableCollection rc = builder.build();
        assertEquals(TEST_FILE_DATA.length * 10, rc.readableBytes());
        byte[] expected = new byte[TEST_FILE_DATA.length * 10];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(TEST_FILE_DATA, 0, expected, i * TEST_FILE_DATA.length, TEST_FILE_DATA.length);
        }
        EmbeddedChannel channel = new EmbeddedChannel();
        for (int removed = 0, step = 12; removed < expected.length;) {
            rc.writeTo(channel, step);
            channel.flush();
            int expectedLength = Math.min(step, expected.length - removed);
            assertArrayEquals(Arrays.copyOfRange(expected, removed, removed + expectedLength),
                    toBytes(channel.outboundMessages()));
            removed += expectedLength;
        }
    }

    @Test
    public void testAddReadableCollection() throws IOException {
        ReadableCollection.Builder builder0 = ReadableCollection.create(ALLOC, 100);
        ReadableCollection.Builder builder1 = ReadableCollection.create(ALLOC, 100);
        for (int i = 0; i < 5; i++) {
            builder0.add(Unpooled.wrappedBuffer(TEST_FILE_DATA)).add(
                    new DefaultFileRegion(TEST_FILE, 0, TEST_FILE_DATA.length));
        }
        ReadableCollection rc0 = builder0.build();
        byte[] expected = new byte[TEST_FILE_DATA.length * 10];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(TEST_FILE_DATA, 0, expected, i * TEST_FILE_DATA.length, TEST_FILE_DATA.length);
        }
        for (int added = 0, step = 12; added < expected.length; added += step) {
            builder1.add(rc0, Math.min(step, expected.length - added));
        }
        ReadableCollection rc1 = builder1.build();
        EmbeddedChannel channel = new EmbeddedChannel();
        rc1.writeTo(channel, rc1.readableBytes());
        channel.flush();
        assertArrayEquals(expected, toBytes(channel.outboundMessages()));
    }
}
