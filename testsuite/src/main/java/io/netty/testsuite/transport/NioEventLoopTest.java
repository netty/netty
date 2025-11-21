/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.transport;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.Executor;

public class NioEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    }

    @Override
    protected EventLoopGroup newAutoScalingEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(SCALING_MAX_THREADS, (Executor) null, AUTO_SCALING_CHOOSER_FACTORY,
                NioIoHandler.newFactory());
    }

    @Override
    protected Channel newChannel(EventLoop eventLoop) {
        return new NioSocketChannel(eventLoop);
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return NioServerSocketChannel.class;
    }
}
