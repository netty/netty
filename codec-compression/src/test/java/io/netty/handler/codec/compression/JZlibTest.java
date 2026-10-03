/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class JZlibTest extends ZlibTest {

    @Override
    protected ZlibEncoder createEncoder(ZlibWrapper wrapper) {
        return new JZlibEncoder(wrapper);
    }

    @Override
    protected ZlibDecoder createDecoder(ZlibWrapper wrapper, int maxAllocation) {
        return new JZlibDecoder(wrapper, maxAllocation);
    }

    @ParameterizedTest
    @CsvSource({ "65537, 2147483647", "33333, 7" })
    public void testHighlyCompressibleRawStreamIsFullyDecoded(int size, int chunkSize) throws Exception {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'a');
        byte[] compressed = rawDeflate(data);

        // Verify the fixture independently before using it to judge JZlibDecoder.
        assertArrayEquals(data, jdkInflate(compressed));

        EmbeddedChannel channel = new EmbeddedChannel(createDecoder(ZlibWrapper.NONE));
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        try {
            for (int offset = 0; offset < compressed.length; offset += chunkSize) {
                int length = Math.min(chunkSize, compressed.length - offset);
                channel.writeInbound(Unpooled.wrappedBuffer(compressed, offset, length).copy());
            }
            channel.finish();

            ByteBuf message;
            while ((message = channel.readInbound()) != null) {
                message.readBytes(decoded, message.readableBytes());
                message.release();
            }
            assertArrayEquals(data, decoded.toByteArray());
        } finally {
            decoded.close();
            channel.close();
        }
    }

    private static byte[] rawDeflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return compressed.toByteArray();
    }

    private static byte[] jdkInflate(byte[] compressed) throws Exception {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        try (InflaterInputStream input = new InflaterInputStream(
                new ByteArrayInputStream(compressed), new Inflater(true))) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                decoded.write(buffer, 0, length);
            }
        }
        return decoded.toByteArray();
    }
}
