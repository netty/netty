/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.traffic;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.example.traffic.HttpStaticFileServerTrafficShaping.ModeTransfer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

public class HttpStaticFileServerTrafficShapingInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslCtx;

    public HttpStaticFileServerTrafficShapingInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }
        if (HttpStaticFileServerTrafficShaping.useGlobalTSH) {
            pipeline.addLast(HttpStaticFileServerTrafficShaping.gtsh);
        }
        pipeline.addLast(new ChannelTrafficShapingHandlerWithLog(1024 * 1024, 1024 * 1024, 1000));
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));
        if (HttpStaticFileServerTrafficShaping.modeTransfer == ModeTransfer.useChunkedFile) {
            pipeline.addLast(new ChunkedWriteHandler());
        }
        // Note that one should have a different EventLoopGroup than
        // TrafficShapingHandler to ensure triggered event are sent in time
        if (HttpStaticFileServerTrafficShaping.modeTransfer == ModeTransfer.useCheckWriteSuspended) {
            pipeline.addLast(HttpStaticFileServerTrafficShaping.businessGroup,
                new HttpStaticFileServerTrafficShapingHandler());
        } else {
            pipeline.addLast(new HttpStaticFileServerTrafficShapingHandler());
        }
    }
}
