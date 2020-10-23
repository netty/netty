/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.tests.DetectPeerCloseWithoutReadTest;

public class EpollDetectPeerCloseWithoutReadTest extends DetectPeerCloseWithoutReadTest {
    @Override
    protected EventLoopGroup newGroup() {
        return new EpollEventLoopGroup(2);
    }

    @Override
    protected Class<? extends ServerChannel> serverChannel() {
        return EpollServerSocketChannel.class;
    }

    @Override
    protected Class<? extends Channel> clientChannel() {
        return EpollSocketChannel.class;
    }
}
