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
package io.netty.handler.codec.http2.hpack;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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
        Huffman.DECODER.decode(buf);
    }

    @Test(expected = IOException.class)
    public void testDecodeIllegalPadding() throws IOException {
        byte[] buf = new byte[1];
        buf[0] = 0x00; // '0', invalid padding
        Huffman.DECODER.decode(buf);
    }

    @Test//(expected = IOException.class) TODO(jpinner) fix me
    public void testDecodeExtraPadding() throws IOException {
        byte[] buf = new byte[2];
        buf[0] = 0x0F; // '1', 'EOS'
        buf[1] = (byte) 0xFF; // 'EOS'
        Huffman.DECODER.decode(buf);
    }

    private void roundTrip(String s) throws IOException {
        roundTrip(Huffman.ENCODER, Huffman.DECODER, s);
    }

    private static void roundTrip(HuffmanEncoder encoder, HuffmanDecoder decoder, String s)
            throws IOException {
        roundTrip(encoder, decoder, s.getBytes());
    }

    private void roundTrip(byte[] buf) throws IOException {
        roundTrip(Huffman.ENCODER, Huffman.DECODER, buf);
    }

    private static void roundTrip(HuffmanEncoder encoder, HuffmanDecoder decoder, byte[] buf)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        encoder.encode(dos, buf);

        byte[] actualBytes = decoder.decode(baos.toByteArray());

        Assert.assertTrue(Arrays.equals(buf, actualBytes));
    }
}
