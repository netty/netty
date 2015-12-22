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

import static org.junit.Assert.*;
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

/**
 * Test for {@link SlicedFileRegion}.
 */
public class SlicedFileRegionTest {

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

    @Test
    public void test() throws IOException {
        DefaultFileRegion region = new DefaultFileRegion(TEST_FILE, 0, TEST_DATA.length);
        assertEquals(0, region.position());
        assertTrue(region.isTransferable());
        assertEquals(TEST_DATA.length, region.transferableBytes());
        assertEquals(0, region.transferIndex());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel ch = Channels.newChannel(bos);
        while (region.transferableBytes() > TEST_DATA.length / 2) {
            region.transferBytesTo(ch, TEST_DATA.length / 2 - region.transferIndex());
        }
        assertArrayEquals(Arrays.copyOf(TEST_DATA, TEST_DATA.length / 2), bos.toByteArray());

        FileRegion subRegion1 = region.transferSlice(TEST_DATA.length / 2).retain();
        assertFalse(region.isTransferable());
        assertEquals(0, region.transferableBytes());
        assertEquals(TEST_DATA.length, region.transferIndex());
        region.release();

        assertEquals(TEST_DATA.length / 2, subRegion1.position());
        assertTrue(subRegion1.isTransferable());
        assertEquals(TEST_DATA.length / 2, subRegion1.transferableBytes());
        assertEquals(0, subRegion1.transferIndex());

        bos.reset();
        while (subRegion1.transferableBytes() > TEST_DATA.length / 4) {
            subRegion1.transferBytesTo(ch, TEST_DATA.length / 4 - subRegion1.transferIndex());
        }
        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length / 2, TEST_DATA.length - TEST_DATA.length / 4),
                bos.toByteArray());

        FileRegion subRegion2 = subRegion1.transferSlice(TEST_DATA.length / 4).retain();
        assertFalse(subRegion1.isTransferable());
        assertEquals(0, subRegion1.transferableBytes());
        assertEquals(TEST_DATA.length / 2, subRegion1.transferIndex());
        subRegion1.release();

        assertEquals(TEST_DATA.length - TEST_DATA.length / 4, subRegion2.position());
        bos.reset();
        while (subRegion2.isTransferable()) {
            subRegion2.transferBytesTo(ch, subRegion2.transferableBytes());
        }
        assertArrayEquals(Arrays.copyOfRange(TEST_DATA, TEST_DATA.length - TEST_DATA.length / 4, TEST_DATA.length),
                bos.toByteArray());
        subRegion2.release();
    }
}
