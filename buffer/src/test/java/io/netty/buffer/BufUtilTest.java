/*
 * Copyright 2013 The Netty Project
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
package io.netty.buffer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import io.netty.util.CharsetUtil;

import org.junit.Test;

public class BufUtilTest {
    @Test
    public void testValidUtf8() {
        // 1-byte sequences
        assertTrue(BufUtil.isValidUtf8(Unpooled.copiedBuffer("Dollar: \u0024 test", CharsetUtil.UTF_8)));
        // 2-byte sequences
        assertTrue(BufUtil.isValidUtf8(Unpooled.copiedBuffer("Cent: \u00a2 test", CharsetUtil.UTF_8)));
        // 3-byte sequences
        assertTrue(BufUtil.isValidUtf8(Unpooled.copiedBuffer("Euro: \u20ac test", CharsetUtil.UTF_8)));
        // 4-byte sequences
        assertTrue(BufUtil.isValidUtf8(Unpooled.copiedBuffer("Han character: " + new String(Character.toChars(0x24b62)) + " test", CharsetUtil.UTF_8)));

        // invalid byte sequence - start of 2-byte sequence followed by a start of 1-byte sequence
        // rather than a sequence continuation
        assertFalse(BufUtil.isValidUtf8(Unpooled.wrappedBuffer(new byte[] { -66, 24 })));
        // incomplete byte sequence - start of 2-byte sequence without a continuation
        assertFalse(BufUtil.isValidUtf8(Unpooled.wrappedBuffer(new byte[] { -62 })));
        // overly long byte sequence - this is a Euro (20ac) character padded with extra 0 bits
        // to make it a 4-byte sequence
        assertFalse(BufUtil.isValidUtf8(Unpooled.wrappedBuffer(new byte[] { -16, -126, -126, -84 })));
    }

    @Test
    public void testValidAscii() {
        assertTrue(BufUtil.isValidAscii(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)));
        assertFalse(BufUtil.isValidAscii(Unpooled.wrappedBuffer(new byte[] { -127 })));
    }
}
