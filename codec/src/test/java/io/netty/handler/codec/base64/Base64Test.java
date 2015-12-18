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
package io.netty.handler.codec.base64;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import org.junit.Test;


import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.junit.Assert.assertEquals;

public class Base64Test {

    @Test
    public void testNotAddNewLineWhenEndOnLimit() {
        ByteBuf src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcde",
                CharsetUtil.US_ASCII);
        ByteBuf expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5emFiY2Rl",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    @Test
    public void testAddNewLine() {
        ByteBuf src = copiedBuffer("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz12345678",
                CharsetUtil.US_ASCII);
        ByteBuf expectedEncoded =
                copiedBuffer("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ejEyMzQ1\nNjc4",
                        CharsetUtil.US_ASCII);
        testEncode(src, expectedEncoded);
    }

    private static void testEncode(ByteBuf src, ByteBuf expectedEncoded) {
        ByteBuf encoded = Base64.encode(src, true, Base64Dialect.STANDARD);
        try {
            assertEquals(expectedEncoded, encoded);
        } finally {
            src.release();
            expectedEncoded.release();
            encoded.release();
        }
    }
}
