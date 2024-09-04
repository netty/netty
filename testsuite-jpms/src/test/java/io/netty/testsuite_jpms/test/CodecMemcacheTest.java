/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequest;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestEncoder;
import io.netty.handler.codec.memcache.binary.DefaultBinaryMemcacheRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CodecMemcacheTest {

    @Test
    public void testEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new BinaryMemcacheRequestEncoder());
        BinaryMemcacheRequest request = new DefaultBinaryMemcacheRequest();
        assertTrue(channel.writeOutbound(request));
        assertTrue(channel.finish());
        ByteBuf written = channel.readOutbound();
        assertEquals(24, written.readableBytes());
        assertEquals(24, written.readableBytes());
        assertEquals((byte) 0x80, written.readByte());
        assertEquals((byte) 0x00, written.readByte());
        written.release();
    }
}
