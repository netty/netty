/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.udt.file;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.udt.UdtChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.util.CharsetUtil;

/**
 * Creates a newly configured {@link ChannelPipeline} for a client-side channel.
 */
public class UdtFileClientInitializer extends ChannelInitializer<UdtChannel> {

    private final SslContext sslCtx;

    public UdtFileClientInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    protected void initChannel(UdtChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc(), UdtFileClient.HOST, UdtFileClient.PORT));
        }

        // Add an encoder for encoding the name of a file name to download.
        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));

        // Add a decoder for decoding the response from a server.
        pipeline.addLast(new UdtFileResponseDecoder());

        // Add the business logic.
        pipeline.addLast(new UdtFileClientHandler());
    }
}
