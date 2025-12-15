/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;

/**
 * Channel initializer for WebTransport channels
 */
public class WebTransportChannelInitializer extends ChannelInitializer<QuicChannel> {
    
    private final WebTransportObserver observer;
    private final ChannelHandler http3Handler;
    
    public WebTransportChannelInitializer(WebTransportObserver observer, ChannelHandler http3Handler) {
        this.observer = observer;
        this.http3Handler = http3Handler;
    }
    
    @Override
    protected void initChannel(QuicChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(http3Handler);
        pipeline.addLast(new WebTransportHandler(observer));
    }
}