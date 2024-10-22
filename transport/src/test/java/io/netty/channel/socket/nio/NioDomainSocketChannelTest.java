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
package io.netty.channel.socket.nio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

@EnabledForJreRange(min = JRE.JAVA_16)
class NioDomainSocketChannelTest {
    @Test
    void accessParent() throws IOException {
        NioServerDomainSocketChannel parent = new NioServerDomainSocketChannel();
        SocketChannel ch = NioDomainSocketChannel.newChannel(SelectorProvider.provider());
        NioDomainSocketChannel child = new NioDomainSocketChannel(parent, ch);
        Assertions.assertSame(parent, child.parent());
        ch.close();
    }
}
