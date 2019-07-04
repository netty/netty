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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class HpackHuffmanTest {

    @Test
    public void testHuffman() throws Http2Exception {
        String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < s.length(); i++) {
            roundTrip(s.substring(0, i));
        }

        Random random = new Random(123456789L);
        byte[] buf = new byte[4096];
        random.nextBytes(buf);
        roundTrip(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeEOS() throws Http2Exception {
        byte[] buf = new byte[4];
        for (int i = 0; i < 4; i++) {
            buf[i] = (byte) 0xFF;
        }
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeIllegalPadding() throws Http2Exception {
        byte[] buf = new byte[1];
        buf[0] = 0x00; // '0', invalid padding
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding() throws Http2Exception {
        byte[] buf = makeBuf(0x0f, 0xFF); // '1', 'EOS'
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding1byte() throws Http2Exception {
        byte[] buf = makeBuf(0xFF);
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding2byte() throws Http2Exception {
        byte[] buf = makeBuf(0x1F, 0xFF); // 'a'
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding3byte() throws Http2Exception {
        byte[] buf = makeBuf(0x1F, 0xFF, 0xFF); // 'a'
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding4byte() throws Http2Exception {
        byte[] buf = makeBuf(0x1F, 0xFF, 0xFF, 0xFF); // 'a'
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodeExtraPadding29bit() throws Http2Exception {
        byte[] buf = makeBuf(0xFF, 0x9F, 0xFF, 0xFF, 0xFF);  // '|'
        decode(buf);
    }

    @Test(expected = Http2Exception.class)
    public void testDecodePartialSymbol() throws Http2Exception {
        byte[] buf = makeBuf(0x52, 0xBC, 0x30, 0xFF, 0xFF, 0xFF, 0xFF); // " pFA\x00", 31 bits of padding, a.k.a. EOS
        decode(buf);
    }

    private static byte[] makeBuf(int ... bytes) {
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = (byte) bytes[i];
        }
        return buf;
    }

    private static void roundTrip(String s) throws Http2Exception {
        roundTrip(new HpackHuffmanEncoder(), s);
    }

    private static void roundTrip(HpackHuffmanEncoder encoder, String s)
            throws Http2Exception {
        roundTrip(encoder, s.getBytes());
    }

    private static void roundTrip(byte[] buf) throws Http2Exception {
        roundTrip(new HpackHuffmanEncoder(), buf);
    }

    private static void roundTrip(HpackHuffmanEncoder encoder, byte[] buf)
            throws Http2Exception {
        ByteBuf buffer = Unpooled.buffer();
        try {
            encoder.encode(buffer, new AsciiString(buf, false));
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);

            byte[] actualBytes = decode(bytes);

            Assert.assertTrue(Arrays.equals(buf, actualBytes));
        } finally {
            buffer.release();
        }
    }

    private static byte[] decode(byte[] bytes) throws Http2Exception {
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            AsciiString decoded = new HpackHuffmanDecoder().decode(buffer, buffer.readableBytes());
            Assert.assertFalse(buffer.isReadable());
            return decoded.toByteArray();
        } finally {
            buffer.release();
        }
    }
}
