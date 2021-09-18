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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FullMemcacheMessageResponseTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = new EmbeddedChannel(
                new BinaryMemcacheResponseEncoder(),
                new BinaryMemcacheResponseDecoder(),
                new BinaryMemcacheObjectAggregator(1024));
    }

    @AfterEach
    public void teardown() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void testEncodeDecode() throws Exception {
        ByteBuf key = Unpooled.wrappedBuffer("key".getBytes(CharsetUtil.UTF_8));
        ByteBuf content = Unpooled.wrappedBuffer("content".getBytes(CharsetUtil.UTF_8));
        ByteBuf extras = Unpooled.wrappedBuffer("extras".getBytes(CharsetUtil.UTF_8));
        FullBinaryMemcacheResponse resp = new DefaultFullBinaryMemcacheResponse(key, extras, content);
        assertTrue(channel.writeOutbound(resp));
        // header + content
        assertEquals(2, channel.outboundMessages().size());
        assertTrue(channel.writeInbound(channel.readOutbound(), channel.readOutbound()));

        FullBinaryMemcacheResponse read = channel.readInbound();
        assertEquals("key", read.key().toString(CharsetUtil.UTF_8));
        assertEquals("content", read.content().toString(CharsetUtil.UTF_8));
        assertEquals("extras", read.extras().toString(CharsetUtil.UTF_8));
        read.release();
    }
}
