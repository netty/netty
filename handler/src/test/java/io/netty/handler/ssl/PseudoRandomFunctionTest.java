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

package io.netty.handler.ssl;

import io.netty.util.CharsetUtil;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
/**
 * The test vectors here were provided via:
 * https://www.ietf.org/mail-archive/web/tls/current/msg03416.html
 */
public class PseudoRandomFunctionTest {

    @Test
    public void testPrfSha256() {
        byte[] secret = Hex.decode("9b be 43 6b a9 40 f0 17 b1 76 52 84 9a 71 db 35");
        byte[] seed = Hex.decode("a0 ba 9f 93 6c da 31 18 27 a6 f7 96 ff d5 19 8c");
        byte[] label = "test label".getBytes(CharsetUtil.US_ASCII);
        byte[] expected = Hex.decode(
                 "e3 f2 29 ba 72 7b e1 7b" +
                 "8d 12 26 20 55 7c d4 53" +
                 "c2 aa b2 1d 07 c3 d4 95" +
                 "32 9b 52 d4 e6 1e db 5a" +
                 "6b 30 17 91 e9 0d 35 c9" +
                 "c9 a4 6b 4e 14 ba f9 af" +
                 "0f a0 22 f7 07 7d ef 17" +
                 "ab fd 37 97 c0 56 4b ab" +
                 "4f bc 91 66 6e 9d ef 9b" +
                 "97 fc e3 4f 79 67 89 ba" +
                 "a4 80 82 d1 22 ee 42 c5" +
                 "a7 2e 5a 51 10 ff f7 01" +
                 "87 34 7b 66");
        byte[] actual = PseudoRandomFunction.hash(secret, label, seed, expected.length, "HmacSha256");
        assertArrayEquals(expected, actual);
    }
}
