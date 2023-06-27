/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.string;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LineEncoderTest {

    @Test
    public void testEncode() {
        testLineEncode(LineSeparator.DEFAULT, "abc");
        testLineEncode(LineSeparator.WINDOWS, "abc");
        testLineEncode(LineSeparator.UNIX, "abc");
    }

    private static void testLineEncode(LineSeparator lineSeparator, String msg) {
        EmbeddedChannel channel = new EmbeddedChannel(new LineEncoder(lineSeparator, CharsetUtil.UTF_8));
        assertTrue(channel.writeOutbound(msg));
        ByteBuf buf = channel.readOutbound();
        try {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            byte[] expected = (msg + lineSeparator.value()).getBytes(CharsetUtil.UTF_8);
            assertArrayEquals(expected, data);
            assertNull(channel.readOutbound());
        } finally {
            buf.release();
            assertFalse(channel.finish());
        }
    }
}
