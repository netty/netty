/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FastLzTest {

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 5000, 65535, 131070 })
    public void testRoundtripWithReaderIndex(int readerIndex) {
        Random random = new Random(42);
        // Use a small alphabet with short runs so we create runs of three equal bytes followed by a different one.
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length;) {
            byte value = (byte) ('x' + random.nextInt(3));
            int run = 1 + random.nextInt(5);
            for (int j = 0; j < run && i < data.length; j++) {
                data[i++] = value;
            }
        }

        for (int level : new int[] { FastLz.LEVEL_1, FastLz.LEVEL_2 }) {
            ByteBuf in = Unpooled.buffer();
            ByteBuf compressed = Unpooled.buffer(FastLz.calculateOutputBufferLength(data.length));
            ByteBuf decompressed = Unpooled.buffer(data.length);
            try {
                in.writeZero(readerIndex).writeBytes(data);
                in.readerIndex(readerIndex);

                int compressedLength = FastLz.compress(in, in.readerIndex(), data.length, compressed, 0, level);
                int decompressedLength = FastLz.decompress(compressed, 0, compressedLength,
                        decompressed, 0, data.length);

                assertEquals(data.length, decompressedLength, "level " + level);
                decompressed.writerIndex(decompressedLength);
                assertEquals(Unpooled.wrappedBuffer(data), decompressed, "level " + level);
            } finally {
                in.release();
                compressed.release();
                decompressed.release();
            }
        }
    }
}
