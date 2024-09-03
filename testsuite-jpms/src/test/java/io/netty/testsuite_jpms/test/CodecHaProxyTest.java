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
import io.netty.handler.codec.haproxy.HAProxyMessageEncoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CodecHaProxyTest {

    @Test
    public void testDecoder() {
        EmbeddedChannel channel = new EmbeddedChannel(HAProxyMessageEncoder.INSTANCE);
        HAProxyMessage message = new HAProxyMessage(
                HAProxyProtocolVersion.V1, HAProxyCommand.PROXY, HAProxyProxiedProtocol.TCP4,
                "192.168.0.1", "192.168.0.11", 56324, 443);
        assertTrue(channel.writeOutbound(message));
        ByteBuf byteBuf = channel.readOutbound();
        assertEquals("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n",
                byteBuf.toString(CharsetUtil.US_ASCII));
        byteBuf.release();
        assertFalse(channel.finish());
    }
}
