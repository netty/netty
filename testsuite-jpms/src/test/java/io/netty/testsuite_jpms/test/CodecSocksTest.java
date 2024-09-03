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
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdType;
import io.netty.handler.codec.socks.SocksAddressType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CodecSocksTest {

    @Test
    public void testEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new SocksMessageEncoder());
        assertTrue(channel.writeOutbound(new SocksCmdRequest(SocksCmdType.BIND,
                SocksAddressType.IPv4, "54.54.111.253", 1)));
        assertTrue(channel.finish());
        ByteBuf buffer = channel.readOutbound();
        assertTrue(buffer.readableBytes() > 0);
        buffer.release();
        assertNull(channel.readOutbound());
    }
}
