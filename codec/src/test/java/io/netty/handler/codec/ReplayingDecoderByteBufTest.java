/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.Signal;
import org.junit.Test;

import static org.junit.Assert.*;

public class ReplayingDecoderByteBufTest {

    /**
     * See https://github.com/netty/netty/issues/445
     */
    @Test
    public void testGetUnsignedByte() {
        ByteBuf buf = Unpooled.copiedBuffer("TestBuffer", CharsetUtil.ISO_8859_1);
        ReplayingDecoderByteBuf buffer = new ReplayingDecoderByteBuf(buf);

        boolean error;
        int i = 0;
        try {
            for (;;) {
                buffer.getUnsignedByte(i);
                i++;
            }
        } catch (Signal e) {
            error = true;
        }

        assertTrue(error);
        assertEquals(10, i);

        buf.release();
    }

    /**
     * See https://github.com/netty/netty/issues/445
     */
    @Test
    public void testGetByte() {
        ByteBuf buf = Unpooled.copiedBuffer("TestBuffer", CharsetUtil.ISO_8859_1);
        ReplayingDecoderByteBuf buffer = new ReplayingDecoderByteBuf(buf);

        boolean error;
        int i = 0;
        try {
            for (;;) {
                buffer.getByte(i);
                i++;
            }
        } catch (Signal e) {
            error = true;
        }

        assertTrue(error);
        assertEquals(10, i);

        buf.release();
    }

    /**
     * See https://github.com/netty/netty/issues/445
     */
    @Test
    public void testGetBoolean() {
        ByteBuf buf = Unpooled.buffer(10);
        while (buf.isWritable()) {
            buf.writeBoolean(true);
        }
        ReplayingDecoderByteBuf buffer = new ReplayingDecoderByteBuf(buf);

        boolean error;
        int i = 0;
        try {
            for (;;) {
                buffer.getBoolean(i);
                i++;
            }
        } catch (Signal e) {
            error = true;
        }

        assertTrue(error);
        assertEquals(10, i);

        buf.release();
    }

}
