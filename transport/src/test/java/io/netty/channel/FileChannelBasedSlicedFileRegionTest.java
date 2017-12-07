/*
 * Copyright 2017 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.ThreadLocalRandom;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@link FileChannelBasedSlicedFileRegion}.
 */
public final class FileChannelBasedSlicedFileRegionTest {

    private static byte[] TEST_DATA = new byte[1024 * 64];

    private static File TEST_FILE;

    @BeforeClass
    public static void setUp() throws IOException {
        ThreadLocalRandom.current().nextBytes(TEST_DATA);
        TEST_FILE = File.createTempFile("netty-", ".tmp");
        TEST_FILE.deleteOnExit();
        FileOutputStream out = new FileOutputStream(TEST_FILE);
        try {
            out.write(TEST_DATA);
        } finally {
            out.close();
        }
    }

    private byte[] transferBytesTo(SliceableFileRegion region, int length) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel ch = Channels.newChannel(bos);
        try {
            while (bos.size() < length && region.isTransferable()) {
                region.transferBytesTo(ch, length - bos.size());
            }
        } finally {
            ch.close();
        }
        return bos.toByteArray();
    }

    private byte[] transferTo(FileRegion region) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel ch = Channels.newChannel(bos);
        try {
            while (region.transferred() < region.count()) {
                region.transferTo(ch, region.transferred());
            }
        } finally {
            ch.close();
        }
        return bos.toByteArray();
    }

    @Test
    public void testSlice() throws IOException {
        DefaultFileRegion region = new DefaultFileRegion(TEST_FILE, 0, TEST_DATA.length);

        SliceableFileRegion subRegion1 = region.retainedSlice(TEST_DATA.length / 2,
                TEST_DATA.length / 2);

        assertEquals(0, region.position());
        assertTrue(region.isTransferable());
        assertEquals(TEST_DATA.length, region.transferableBytes());
        assertEquals(0, region.transferIndex());

        assertEquals(TEST_DATA.length / 2, subRegion1.position());
        assertTrue(subRegion1.isTransferable());
        assertEquals(TEST_DATA.length / 2, subRegion1.transferableBytes());
        assertEquals(0, subRegion1.transferIndex());

        assertArrayEquals(TEST_DATA, transferBytesTo(region, Integer.MAX_VALUE));
        assertEquals(TEST_DATA.length, region.transferIndex());
        assertFalse(region.isTransferable());
        region.release();

        SliceableFileRegion subRegion2 = subRegion1.retainedSlice(TEST_DATA.length / 4,
                TEST_DATA.length / 4);

        assertEquals(TEST_DATA.length / 4 * 3, subRegion2.position());
        assertTrue(subRegion2.isTransferable());
        assertEquals(TEST_DATA.length / 4, subRegion2.transferableBytes());
        assertEquals(0, subRegion2.transferIndex());

        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length / 2, TEST_DATA.length),
                transferBytesTo(subRegion1, Integer.MAX_VALUE));
        assertEquals(TEST_DATA.length / 2, subRegion1.transferIndex());
        assertFalse(subRegion1.isTransferable());
        subRegion1.release();

        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length / 4 * 3, TEST_DATA.length),
                transferBytesTo(subRegion2, Integer.MAX_VALUE));
        assertEquals(TEST_DATA.length / 4, subRegion2.transferIndex());
        assertFalse(subRegion2.isTransferable());
        subRegion2.release();
    }

    @Test
    public void testTransferSlice() throws IOException {
        DefaultFileRegion region = new DefaultFileRegion(TEST_FILE, 0, TEST_DATA.length);
        assertEquals(0, region.position());
        assertTrue(region.isTransferable());
        assertEquals(TEST_DATA.length, region.transferableBytes());
        assertEquals(0, region.transferIndex());

        assertArrayEquals(Arrays.copyOf(TEST_DATA, TEST_DATA.length / 2),
                transferBytesTo(region, TEST_DATA.length / 2));

        SliceableFileRegion subRegion1 = region.transferRetainedSlice(TEST_DATA.length / 2);
        assertFalse(region.isTransferable());
        assertEquals(0, region.transferableBytes());
        assertEquals(TEST_DATA.length, region.transferIndex());
        region.release();

        assertEquals(TEST_DATA.length / 2, subRegion1.position());
        assertTrue(subRegion1.isTransferable());
        assertEquals(TEST_DATA.length / 2, subRegion1.transferableBytes());
        assertEquals(0, subRegion1.transferIndex());

        assertArrayEquals(
                Arrays.copyOfRange(TEST_DATA, TEST_DATA.length / 2,
                        TEST_DATA.length - TEST_DATA.length / 4),
                transferBytesTo(subRegion1, TEST_DATA.length / 4));

        SliceableFileRegion subRegion2 = subRegion1.transferRetainedSlice(TEST_DATA.length / 4);
        assertFalse(subRegion1.isTransferable());
        assertEquals(0, subRegion1.transferableBytes());
        assertEquals(TEST_DATA.length / 2, subRegion1.transferIndex());
        subRegion1.release();

        assertEquals(TEST_DATA.length - TEST_DATA.length / 4, subRegion2.position());
        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length - TEST_DATA.length / 4,
                TEST_DATA.length), transferBytesTo(subRegion2, TEST_DATA.length / 4));
        subRegion2.release();
    }

    @Test
    public void testOldTransferTo() throws IOException {
        DefaultFileRegion region = new DefaultFileRegion(TEST_FILE, 0, TEST_DATA.length);

        SliceableFileRegion subRegion1 = region.transferRetainedSlice(TEST_DATA.length / 2);
        assertArrayEquals(Arrays.copyOf(TEST_DATA, TEST_DATA.length / 2), transferTo(subRegion1));
        assertEquals(subRegion1.count(), subRegion1.transferred());

        SliceableFileRegion subRegion2 = region.transferRetainedSlice(TEST_DATA.length / 2);
        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length / 2, TEST_DATA.length),
                transferTo(subRegion2));
        assertEquals(subRegion2.count(), subRegion2.transferred());

        subRegion1.release();
        subRegion2.release();
        region.release();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReferenceCountError() throws IOException {
        DefaultFileRegion region = new DefaultFileRegion(TEST_FILE, 0, TEST_DATA.length);
        region.release();
        transferBytesTo(region, TEST_DATA.length);
    }
}
