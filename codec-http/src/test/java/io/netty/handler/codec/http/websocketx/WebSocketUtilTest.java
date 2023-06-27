/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.EmptyArrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketUtilTest {

    // how many times do we want to run each random variable checker
    private static final int NUM_ITERATIONS = 1000;

    private static void assertRandomWithinBoundaries(int min, int max) {
        int r = WebSocketUtil.randomNumber(min, max);
        assertTrue(min <= r && r <= max);
    }

    @Test
    public void testRandomNumberGenerator() {
        int iteration = 0;
        while (++iteration < NUM_ITERATIONS) {
            assertRandomWithinBoundaries(0, 1);
            assertRandomWithinBoundaries(0, 1);
            assertRandomWithinBoundaries(-1, 1);
            assertRandomWithinBoundaries(-1, 0);
        }
    }

    @Test
    public void testBase64() {
        String base64 = WebSocketUtil.base64(EmptyArrays.EMPTY_BYTES);
        assertNotNull(base64);
        assertTrue(base64.isEmpty());

        base64 = WebSocketUtil.base64("foo".getBytes(CharsetUtil.UTF_8));
        assertEquals(base64, "Zm9v");

        base64 = WebSocketUtil.base64("bar".getBytes(CharsetUtil.UTF_8));
        ByteBuf src = Unpooled.wrappedBuffer(base64.getBytes(CharsetUtil.UTF_8));
        try {
            ByteBuf dst = Base64.decode(src);
            try {
                assertEquals(new String(ByteBufUtil.getBytes(dst), CharsetUtil.UTF_8), "bar");
            } finally {
                dst.release();
            }
        } finally {
            src.release();
        }
    }
}
