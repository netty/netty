/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static io.netty.handler.codec.compression.Bzip2Constants.*;
import static org.junit.Assert.fail;

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
    public void initChannel() {
        channel = new EmbeddedChannel(new Bzip2Decoder());
    }

    private void writeInboundDestroyAndExpectDecompressionException(ByteBuf in) {
        try {
            channel.writeInbound(in);
        } finally {
            try {
                destroyChannel();
                fail();
            } catch (DecompressionException ignored) {
                // expected
            }
        }
    }

    @Test
    public void testUnexpectedStreamIdentifier() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected stream identifier contents");

        ByteBuf in = Unpooled.buffer();
        in.writeLong(1823080128301928729L); //random value
        writeInboundDestroyAndExpectDecompressionException(in);
    }

    @Test
    public void testInvalidBlockSize() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("block size is invalid");

        ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('0');  //incorrect block size

        channel.writeInbound(in);
    }

    @Test
    public void testBadBlockHeader() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("bad block header");

        ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('1');  //block size
        in.writeMedium(11); //incorrect block header
        in.writeMedium(11); //incorrect block header
        in.writeInt(11111); //block CRC

        channel.writeInbound(in);
    }

    @Test
    public void testStreamCrcErrorOfEmptyBlock() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("stream CRC error");

        ByteBuf in = Unpooled.buffer();
        in.writeMedium(MAGIC_NUMBER);
        in.writeByte('1');  //block size
        in.writeMedium(END_OF_STREAM_MAGIC_1);
        in.writeMedium(END_OF_STREAM_MAGIC_2);
        in.writeInt(1);  //wrong storedCombinedCRC

        channel.writeInbound(in);
    }

    @Test
    public void testStreamCrcError() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("stream CRC error");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[41] = (byte) 0xDD;

        tryDecodeAndCatchBufLeaks(channel, Unpooled.wrappedBuffer(data));
    }

    @Test
    public void testIncorrectHuffmanGroupsNumber() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("incorrect huffman groups number");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[25] = 0x70;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testIncorrectSelectorsNumber() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("incorrect selectors number");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[25] = 0x2F;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testBlockCrcError() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("block CRC error");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[11] = 0x77;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        writeInboundDestroyAndExpectDecompressionException(in);
    }

    @Test
    public void testStartPointerInvalid() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("start pointer invalid");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[14] = (byte) 0xFF;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        writeInboundDestroyAndExpectDecompressionException(in);
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
