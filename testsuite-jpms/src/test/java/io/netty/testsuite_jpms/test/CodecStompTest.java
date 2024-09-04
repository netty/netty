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
import io.netty.handler.codec.stomp.StompSubframeEncoder;
import io.netty.handler.codec.stomp.DefaultStompHeadersSubframe;
import io.netty.handler.codec.stomp.StompHeadersSubframe;
import io.netty.handler.codec.stomp.StompCommand;
import io.netty.handler.codec.stomp.StompHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CodecStompTest {

    @Test
    public void testEncoder() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeEncoder());
        StompHeadersSubframe frame = new DefaultStompHeadersSubframe(StompCommand.CONNECT);
        StompHeaders headers = frame.headers();
        headers.set(StompHeaders.HOST, "stomp.github.org");
        headers.set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");
        channel.writeOutbound(frame);
        ByteBuf byteBuf = channel.readOutbound();
        assertEquals("CONNECT\n" +
                "host:stomp.github.org\n" +
                "accept-version:1.1,1.2\n" +
                "\n", byteBuf.toString(StandardCharsets.UTF_8));
        assertNotNull(byteBuf);
        byteBuf.release();
    }
}
