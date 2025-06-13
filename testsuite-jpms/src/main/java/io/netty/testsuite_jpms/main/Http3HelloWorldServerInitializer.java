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
package io.netty.testsuite_jpms.main;

import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;

public class Http3HelloWorldServerInitializer extends ChannelInitializer<QuicChannel> {

    @Override
    protected void initChannel(QuicChannel ch) {
        // Called for each connection
        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                new ChannelInitializer<QuicStreamChannel>() {
                    // Called for each request-stream,
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new Http3HelloWorldServerHandler());
                    }
                }));
    }
}
