/*
  * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.sockjs.handlers.CorsInboundHandler;
import io.netty.handler.codec.sockjs.handlers.CorsOutboundHandler;
import io.netty.handler.codec.sockjs.handlers.SockJsHandler;

/**
 * {@link ChannelInitializer} for Sockjs.
 *
 */
public class SockJsChannelInitializer extends ChannelInitializer<Channel> {

    private final SockJsServiceFactory[] services;

    public SockJsChannelInitializer(final SockJsServiceFactory... services) {
        this.services = services;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("chunkAggregator", new HttpObjectAggregator(130 * 1024));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("corsInbound", new CorsInboundHandler());
        pipeline.addLast("sockjs", new SockJsHandler(services));
        pipeline.addLast("corsOutbound", new CorsOutboundHandler());
    }

}
