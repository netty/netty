/*
 * Copyright 2019 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static io.netty5.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteBufChecksumTest {

    private static final byte[] BYTE_ARRAY = new byte[1024];

    @BeforeAll
    public static void setUp() {
        new Random().nextBytes(BYTE_ARRAY);
    }

    @Test
    public void testHeapByteBufUpdate() {
        testUpdate(BufferAllocator.onHeapUnpooled().copyOf(BYTE_ARRAY));
    }

    @Test
    public void testDirectByteBufUpdate() {
        Buffer buf = BufferAllocator.offHeapUnpooled().allocate(BYTE_ARRAY.length);
        buf.writeBytes(BYTE_ARRAY);
        testUpdate(buf);
    }

    private static void testUpdate(Buffer buf) {
        try (buf) {
            // all variations of xxHash32: slow and naive, optimised, wrapped optimised;
            // the last two should be literally identical, but it's best to guard against
            // an accidental regression in ByteBufChecksum#wrapChecksum(Checksum)
            testUpdate(xxHash32(DEFAULT_SEED), new ByteBufChecksum(xxHash32(DEFAULT_SEED)), buf);
            testUpdate(xxHash32(DEFAULT_SEED), new Lz4XXHash32(DEFAULT_SEED), buf);
            testUpdate(xxHash32(DEFAULT_SEED), new ByteBufChecksum(new Lz4XXHash32(DEFAULT_SEED)), buf);

            // CRC32 and Adler32, special-cased to use ReflectiveByteBufChecksum
            testUpdate(new CRC32(), new ByteBufChecksum(new CRC32()), buf);
            testUpdate(new Adler32(), new ByteBufChecksum(new Adler32()), buf);
        }
    }

    private static void testUpdate(Checksum checksum, ByteBufChecksum wrapped, Buffer buf) {
        testUpdate(checksum, wrapped, buf, 0, BYTE_ARRAY.length);
        testUpdate(checksum, wrapped, buf, 0, BYTE_ARRAY.length - 1);
        testUpdate(checksum, wrapped, buf, 1, BYTE_ARRAY.length - 1);
        testUpdate(checksum, wrapped, buf, 1, BYTE_ARRAY.length - 2);
    }

    private static void testUpdate(Checksum checksum, ByteBufChecksum wrapped, Buffer buf, int off, int len) {
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
