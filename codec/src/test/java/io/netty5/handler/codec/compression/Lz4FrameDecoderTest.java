/*
 * Copyright 2014 The Netty Project
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
import io.netty5.channel.embedded.EmbeddedChannel;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static io.netty5.handler.codec.compression.Lz4Constants.MAX_BLOCK_SIZE;
import static io.netty5.handler.codec.compression.Lz4Constants.MIN_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Lz4FrameDecoderTest extends AbstractDecoderTest {

    private static final byte[] DATA = { 0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x05, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00,  // compr. and decompr. length
                                         (byte) 0x86, (byte) 0xE4, 0x79, 0x0F,            // checksum
                                         0x4E, 0x65, 0x74, 0x74, 0x79,                    // data
                                         0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // last empty block
                                         0x00, 0x00, 0x00, 0x00 };

    public Lz4FrameDecoderTest() throws Exception {
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new DecompressionHandler(Lz4Decompressor.newFactory(true)));
    }

    private void assertIllegalInput(int idx, byte value, String msg) {
        Buffer in = channel.bufferAllocator().copyOf(DATA);
        in.setByte(idx, value);
        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), msg);
    }

    @Test
    public void testUnexpectedBlockIdentifier() {
        assertIllegalInput(1, (byte) 0x00, "unexpected block identifier");
    }

    @Test
    public void testInvalidCompressedLength() {
        assertIllegalInput(12, (byte) 0xFF, "invalid compressedLength");
    }

    @Test
    public void testInvalidDecompressedLength() {
        assertIllegalInput(16, (byte) 0xFF, "invalid decompressedLength");
    }

    @Test
    public void testDecompressedAndCompressedLengthMismatch() {
        assertIllegalInput(13, (byte) 0x01, "mismatch");
    }

    @Test
    public void testUnexpectedBlockType() {
        assertIllegalInput(8, (byte) 0x36, "unexpected blockType");
    }

    @Test
    public void testMismatchingChecksum() {
        assertIllegalInput(17, (byte) 0x01, "mismatching checksum");
    }

    @Test
    public void testChecksumErrorOfLastBlock() {
        Buffer in = channel.bufferAllocator().copyOf(DATA);
        in.setByte(44, (byte) 0x01);

        assertThrows(DecompressionException.class,
            () -> tryDecodeAndCatchBufLeaks(channel, in), "checksum error");
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int size = MAX_BLOCK_SIZE + 1;
        LZ4BlockOutputStream lz4Os = new LZ4BlockOutputStream(os,
                rand.nextInt(size - MIN_BLOCK_SIZE) + MIN_BLOCK_SIZE);
        lz4Os.write(data);
        lz4Os.close();

        return os.toByteArray();
    }
}
