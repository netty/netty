/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static org.junit.Assert.*;

public class ByteBufChecksumTest {

    private static final byte[] BYTE_ARRAY = new byte[1024];

    @BeforeClass
    public static void setUp() {
        new Random().nextBytes(BYTE_ARRAY);
    }

    @Test
    public void testHeapByteBufUpdate() {
        testUpdate(Unpooled.wrappedBuffer(BYTE_ARRAY));
    }

    @Test
    public void testDirectByteBufUpdate() {
        ByteBuf buf = Unpooled.directBuffer(BYTE_ARRAY.length);
        buf.writeBytes(BYTE_ARRAY);
        testUpdate(buf);
    }

    private static void testUpdate(ByteBuf buf) {
        try {
            // all variations of xxHash32: slow and naive, optimised, wrapped optimised;
            // the last two should be literally identical, but it's best to guard against
            // an accidental regression in ByteBufChecksum#wrapChecksum(Checksum)
            testUpdate(xxHash32(DEFAULT_SEED), ByteBufChecksum.wrapChecksum(xxHash32(DEFAULT_SEED)), buf);
            testUpdate(xxHash32(DEFAULT_SEED), new Lz4XXHash32(DEFAULT_SEED), buf);
            testUpdate(xxHash32(DEFAULT_SEED), ByteBufChecksum.wrapChecksum(new Lz4XXHash32(DEFAULT_SEED)), buf);

            // CRC32 and Adler32, special-cased to use ReflectiveByteBufChecksum
            testUpdate(new CRC32(), ByteBufChecksum.wrapChecksum(new CRC32()), buf);
            testUpdate(new Adler32(), ByteBufChecksum.wrapChecksum(new Adler32()), buf);
        } finally {
            buf.release();
        }
    }

    private static void testUpdate(Checksum checksum, ByteBufChecksum wrapped, ByteBuf buf) {
        testUpdate(checksum, wrapped, buf, 0, BYTE_ARRAY.length);
        testUpdate(checksum, wrapped, buf, 0, BYTE_ARRAY.length - 1);
        testUpdate(checksum, wrapped, buf, 1, BYTE_ARRAY.length - 1);
        testUpdate(checksum, wrapped, buf, 1, BYTE_ARRAY.length - 2);
    }

    private static void testUpdate(Checksum checksum, ByteBufChecksum wrapped, ByteBuf buf, int off, int len) {
        checksum.reset();
        wrapped.reset();

        checksum.update(BYTE_ARRAY, off, len);
        wrapped.update(buf, off, len);

        assertEquals(checksum.getValue(), wrapped.getValue());
    }

    private static Checksum xxHash32(int seed) {
        return XXHashFactory.fastestInstance().newStreamingHash32(seed).asChecksum();
    }
}
