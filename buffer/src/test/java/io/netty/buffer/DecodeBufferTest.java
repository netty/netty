/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer;

import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecodeBufferTest {
    private static ByteBuf utf8(String s) {
        return ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(s), StandardCharsets.UTF_8);
    }

    @Test
    public void simple() {
        ByteBuf read1;
        ByteBuf read2;
        ByteBuf read3;

        try (DecodeBuffer db = DecodeBuffer.create(ByteBufAllocator.DEFAULT)) {
            // write ops (total 6 bytes)
            db.add(utf8("foo"));
            db.add(utf8("bar"));

            // read op (3 bytes)
            ByteBuf decode = db.startDecode();
            try {
                read1 = decode.readRetainedSlice(3);
            } finally {
                db.stopDecode(decode);
                decode.release();
            }

            // write op (3 bytes)
            db.add(utf8("baz"));

            // read ops (total 6 bytes)
            decode = db.startDecode();
            try {
                read2 = decode.readRetainedSlice(4);
            } finally {
                db.stopDecode(decode);
                decode.release();
            }

            decode = db.startDecode();
            try {
                read3 = decode.readRetainedSlice(2);
            } finally {
                db.stopDecode(decode);
                decode.release();
            }
        }

        assertEquals("foo", read1.toString(StandardCharsets.UTF_8));
        assertEquals("barb", read2.toString(StandardCharsets.UTF_8));
        assertEquals("az", read3.toString(StandardCharsets.UTF_8));

        read1.release();
        read2.release();
        read3.release();
    }
}