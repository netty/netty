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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static io.netty5.handler.codec.compression.Bzip2Constants.END_OF_STREAM_MAGIC_1;
import static io.netty5.handler.codec.compression.Bzip2Constants.END_OF_STREAM_MAGIC_2;
import static io.netty5.handler.codec.compression.Bzip2Constants.MAGIC_NUMBER;
import static io.netty5.handler.codec.compression.Bzip2Constants.MIN_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Bzip2DecoderTest extends AbstractDecoderTest {

    private static final byte[] DATA = { 0x42, 0x5A, 0x68, 0x37, 0x31, 0x41, 0x59, 0x26, 0x53,
                                         0x59, 0x77, 0x7B, (byte) 0xCA, (byte) 0xC0, 0x00, 0x00,
                                         0x00, 0x05, (byte) 0x80, 0x00, 0x01, 0x02, 0x00, 0x04,
                                         0x20, 0x20, 0x00, 0x30, (byte) 0xCD, 0x34, 0x19, (byte) 0xA6,
                                         (byte) 0x89, (byte) 0x99, (byte) 0xC5, (byte) 0xDC, (byte) 0x91,
                                         0x4E, 0x14, 0x24, 0x1D, (byte) 0xDE, (byte) 0xF2, (byte) 0xB0, 0x00 };

    public Bzip2DecoderTest() throws Exception {
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new DecompressionHandler(Bzip2Decompressor.newFactory()));
    }

    private void writeInboundDestroyAndExpectDecompressionException(Buffer in) {
        try {
            channel.writeInbound(in);
        } finally {
            destroyChannel();
        }
    }

    @Test
    public void testUnexpectedStreamIdentifier() {
        Buffer in = channel.bufferAllocator().allocate(32);
        in.writeLong(1823080128301928729L); //random value
        assertThrows(DecompressionException.class,
            () -> writeInboundDestroyAndExpectDecompressionException(in), "Unexpected stream identifier contents");
    }

    @Test
    public void testInvalidBlockSize() {
        Buffer in = channel.bufferAllocator().allocate(32);
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte((byte) '0');  //incorrect block size

        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), "block size is invalid");
    }

    @Test
    public void testBadBlockHeader() {
        Buffer in = channel.bufferAllocator().allocate(32);
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte((byte) '1');  //block size
        in.writeMedium(11); //incorrect block header
        in.writeMedium(11); //incorrect block header
        in.writeInt(11111); //block CRC

        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), "bad block header");
    }

    @Test
    public void testStreamCrcErrorOfEmptyBlock() {
        Buffer in = channel.bufferAllocator().allocate(32);
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte((byte) '1');  //block size
        in.writeMedium(END_OF_STREAM_MAGIC_1);
        in.writeMedium(END_OF_STREAM_MAGIC_2);
        in.writeInt(1);  //wrong storedCombinedCRC

        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), "stream CRC error");
    }

    private void testInvalidInput(int idx, byte value, String msg) {
        final Buffer data = channel.bufferAllocator().copyOf(DATA);
        data.setByte(idx, value);

        assertThrows(DecompressionException.class,
                () -> tryDecodeAndCatchBufLeaks(channel, data), msg);
    }

    @Test
    public void testStreamCrcError() {
        testInvalidInput(41, (byte) 0xDD, "stream CRC error");
    }

    @Test
    public void testIncorrectHuffmanGroupsNumber() {
        testInvalidInput(25, (byte) 0x70, "incorrect huffman groups number");
    }

    @Test
    public void testIncorrectSelectorsNumber() {
        testInvalidInput(25, (byte) 0x2F, "incorrect selectors number");
    }

    @Test
    public void testBlockCrcError() {
        final Buffer in = channel.bufferAllocator().copyOf(DATA);
        in.setByte(11, (byte) 0x77);
        assertThrows(DecompressionException.class,
            () -> writeInboundDestroyAndExpectDecompressionException(in), "block CRC error");
    }

    @Test
    public void testStartPointerInvalid() {
        final Buffer in = channel.bufferAllocator().copyOf(DATA);
        in.setByte(14, (byte) 0xFF);
        assertThrows(DecompressionException.class,
            () -> writeInboundDestroyAndExpectDecompressionException(in), "start pointer invalid");
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BZip2CompressorOutputStream bZip2Os = new BZip2CompressorOutputStream(os, MIN_BLOCK_SIZE);
        bZip2Os.write(data);
        bZip2Os.close();

        return os.toByteArray();
    }
}
