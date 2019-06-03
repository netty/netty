/*
 *
 *  * Copyright 2019 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License,
 *  * version 2.0 (the "License"); you may not use this file except in compliance
 *  * with the License. You may obtain a copy of the License at:
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *
 */

package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VarIntTest {

    @Test
    public void testReadWrite() {
        for (int i = 0; i < 2000; i++) {
            testValid(i);
        }
    }

    @Test
    public void testConcat() {
        ByteBuf buf = Unpooled.buffer();
        try {
            for (int i = 0; i < 2000; i++) {
                VarInt.byLong(i).write(buf);
            }
            for (int i = 0; i < 2000; i++) {
                assertEquals(i, VarInt.read(buf).asLong());
            }
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    private void testValid(long number, ByteBuf buf) {
        VarInt.byLong(number).write(buf);
        assertEquals("VarInt invalid for " + number, number, VarInt.read(buf).asLong());
    }

    private void testValid(long number) {
        ByteBuf buf = Unpooled.buffer();
        try {
            testValid(number, buf);
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    public void testMax() {
        long MAX = 4611686018427387903L;
        testValid(MAX);
        testValid(MAX - 1000);
    }

}
