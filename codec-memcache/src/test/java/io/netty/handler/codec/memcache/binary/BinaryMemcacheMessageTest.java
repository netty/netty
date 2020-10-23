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
package io.netty.handler.codec.memcache.binary;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BinaryMemcacheMessageTest {

    @Test
    public void testSetLengths() {
        ByteBuf key = Unpooled.copiedBuffer("Netty  Rocks!", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.copiedBuffer("some extras", CharsetUtil.UTF_8);
        ByteBuf content = Unpooled.copiedBuffer("content", CharsetUtil.UTF_8);
        try {
            testSettingLengths(new DefaultBinaryMemcacheRequest(), 0, 0, 0);
            testSettingLengths(new DefaultBinaryMemcacheRequest(key.retain()), key.readableBytes(), 0, 0);
            testSettingLengths(new DefaultBinaryMemcacheRequest(key.retain(), extras.retain()),
                    key.readableBytes(), extras.readableBytes(), 0);

            testSettingLengths(new DefaultBinaryMemcacheResponse(), 0, 0, 0);
            testSettingLengths(new DefaultBinaryMemcacheResponse(key.retain()), key.readableBytes(), 0, 0);
            testSettingLengths(new DefaultBinaryMemcacheResponse(key.retain(), extras.retain()),
                    key.readableBytes(), extras.readableBytes(), 0);

            testSettingLengths(new DefaultFullBinaryMemcacheRequest(key.retain(), extras.retain()),
                    key.readableBytes(), extras.readableBytes(), 0);
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(null, extras.retain()),
                    0, extras.readableBytes(), 0);
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(key.retain(), null),
                    key.readableBytes(), 0, 0);
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(null, null), 0, 0, 0);
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(key.retain(), extras.retain(), content.retain()),
                    key.readableBytes(), extras.readableBytes(), content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(null, extras.retain(), content.retain()),
                    0, extras.readableBytes(), content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(key.retain(), null, content.retain()),
                    key.readableBytes(), 0, content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheRequest(null, null, content.retain()),
                    0, 0, content.readableBytes());

            testSettingLengths(new DefaultFullBinaryMemcacheResponse(key.retain(), extras.retain()),
                    key.readableBytes(), extras.readableBytes(), 0);
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(null, extras.retain()),
                    0, extras.readableBytes(), 0);
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(key.retain(), null),
                    key.readableBytes(), 0, 0);
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(null, null), 0, 0, 0);
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(key.retain(), extras.retain(), content.retain()),
                    key.readableBytes(), extras.readableBytes(), content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(null, extras.retain(), content.retain()),
                    0, extras.readableBytes(), content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(key.retain(), null, content.retain()),
                    key.readableBytes(), 0, content.readableBytes());
            testSettingLengths(new DefaultFullBinaryMemcacheResponse(null, null, content.retain()),
                    0, 0, content.readableBytes());
        } finally {
            key.release();
            extras.release();
            content.release();
        }
    }

    private static void testSettingLengths(BinaryMemcacheMessage message,
                                    int initialKeyLength, int initialExtrasLength, int contentLength) {
        ByteBuf key = Unpooled.copiedBuffer("netty", CharsetUtil.UTF_8);
        ByteBuf extras = Unpooled.copiedBuffer("extras", CharsetUtil.UTF_8);
        ByteBuf key2 = Unpooled.copiedBuffer("netty!", CharsetUtil.UTF_8);
        ByteBuf extras2 = Unpooled.copiedBuffer("extras!", CharsetUtil.UTF_8);
        try {
            assertEquals(initialKeyLength, message.keyLength());
            assertEquals(initialExtrasLength, message.extrasLength());
            assertEquals(initialKeyLength + initialExtrasLength + contentLength, message.totalBodyLength());

            message.setKey(key.retain());
            assertEquals(key.readableBytes(), message.keyLength());
            assertEquals(initialExtrasLength, message.extrasLength());
            assertEquals(key.readableBytes() + initialExtrasLength + contentLength, message.totalBodyLength());

            message.setExtras(extras.retain());
            assertEquals(key.readableBytes(), message.keyLength());
            assertEquals(extras.readableBytes(), message.extrasLength());
            assertEquals(key.readableBytes() + extras.readableBytes() + contentLength, message.totalBodyLength());

            // Replace the previous key
            message.setKey(key2.retain());
            assertEquals(key2.readableBytes(), message.keyLength());
            assertEquals(extras.readableBytes(), message.extrasLength());
            assertEquals(key2.readableBytes() + extras.readableBytes() + contentLength, message.totalBodyLength());

            // Replace the previous extras
            message.setExtras(extras2.retain());
            assertEquals(key2.readableBytes(), message.keyLength());
            assertEquals(extras2.readableBytes(), message.extrasLength());
            assertEquals(key2.readableBytes() + extras2.readableBytes() + contentLength, message.totalBodyLength());
        } finally {
            key.release();
            extras.release();
            key2.release();
            extras2.release();
            message.release();
        }
    }
}
