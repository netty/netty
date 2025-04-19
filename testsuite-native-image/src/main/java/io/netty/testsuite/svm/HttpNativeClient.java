/*
 * Copyright 2025 The Netty Project
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
package io.netty.testsuite.svm;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpClientCodec;

public class HttpNativeClient {

    private final int port;

    private final EventLoopGroup group;

    private final Class<? extends Channel> channelType;

    public HttpNativeClient(int port, EventLoopGroup group, Class<? extends Channel> channelType) {
        this.port = port;
        this.group = group;
        this.channelType = channelType;
    }

    public Channel initClient() throws InterruptedException {
        Channel clientChannel = new Bootstrap()
                .group(group)
                .channel(channelType)
                .handler(new HttpClientCodec())
                .connect("localhost", port)
                .sync().channel();
        return clientChannel;
    }
}
