/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2.internal.hpack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class HuffmanTest {

    @Test
    public void testHuffman() throws IOException {
        String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < s.length(); i++) {
            roundTrip(s.substring(0, i));
        }

        Random random = new Random(123456789L);
        byte[] buf = new byte[4096];
        random.nextBytes(buf);
        roundTrip(buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeEOS() throws IOException {
        byte[] buf = new byte[4];
        for (int i = 0; i < 4; i++) {
            buf[i] = (byte) 0xFF;
        }
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeIllegalPadding() throws IOException {
        byte[] buf = new byte[1];
        buf[0] = 0x00; // '0', invalid padding
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding() throws IOException {
        byte[] buf = makeBuf(0x0f, 0xFF); // '1', 'EOS'
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding1byte() throws IOException {
        byte[] buf = makeBuf(0xFF);
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding2byte() throws IOException {
        byte[] buf = makeBuf(0x1F, 0xFF); // 'a'
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding3byte() throws IOException {
        byte[] buf = makeBuf(0x1F, 0xFF, 0xFF); // 'a'
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding4byte() throws IOException {
        byte[] buf = makeBuf(0x1F, 0xFF, 0xFF, 0xFF); // 'a'
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeExtraPadding29bit() throws IOException {
        byte[] buf = makeBuf(0xFF, 0x9F, 0xFF, 0xFF, 0xFF);  // '|'
        decode(newHuffmanDecoder(), buf);
    }

    @Test(expected = IOException.class)
    public void testDecodePartialSymbol() throws IOException {
        byte[] buf = makeBuf(0x52, 0xBC, 0x30, 0xFF, 0xFF, 0xFF, 0xFF); // " pFA\x00", 31 bits of padding, a.k.a. EOS
        decode(newHuffmanDecoder(), buf);
    }

    private static byte[] makeBuf(int ... bytes) {
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) bytes[i];
        }
        return buf;
    }

    private static void roundTrip(String s) throws IOException {
        roundTrip(new HuffmanEncoder(), newHuffmanDecoder(), s);
    }

    private static void roundTrip(HuffmanEncoder encoder, HuffmanDecoder decoder, String s)
            throws IOException {
        roundTrip(encoder, decoder, s.getBytes());
    }

    private static void roundTrip(byte[] buf) throws IOException {
        roundTrip(new HuffmanEncoder(), newHuffmanDecoder(), buf);
    }

    private static void roundTrip(HuffmanEncoder encoder, HuffmanDecoder decoder, byte[] buf)
            throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        try {
            encoder.encode(buffer, new AsciiString(buf, false));
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);

            byte[] actualBytes = decode(decoder, bytes);

            Assert.assertTrue(Arrays.equals(buf, actualBytes));
        } finally {
            buffer.release();
        }
    }

    private static byte[] decode(HuffmanDecoder decoder, byte[] bytes) throws IOException {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            AsciiString decoded = decoder.decode(buffer, buffer.readableBytes());
            Assert.assertFalse(buffer.isReadable());
            return decoded.toByteArray();
        } finally {
            buffer.release();
        }
    }

    private static HuffmanDecoder newHuffmanDecoder() {
        return new HuffmanDecoder(32);
    }
}
