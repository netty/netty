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
package io.netty.handler.codec.http3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * WebTransport通道初始化器，用于配置WebTransport通道处理器。
 */
public class WebTransportChannelInitializer extends ChannelInitializer<Channel> {

    private final SslContext sslContext;
    private final WebTransportObserver observer;
    private final boolean isServer;

    public WebTransportChannelInitializer(SslContext sslContext, WebTransportObserver observer, boolean isServer) {
        this.sslContext = sslContext;
        this.observer = observer != null ? observer : NoopWebTransportObserver.INSTANCE;
        this.isServer = isServer;
    }

    public WebTransportChannelInitializer(SslContext sslContext, WebTransportObserver observer) {
        this(sslContext, observer, true);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        SSLEngine sslEngine = sslContext.newEngine(ch.alloc());
        // 根据初始化器的模式设置SSL模式
        sslEngine.setUseClientMode(!isServer);
        // 启用TLSv1.3
        sslEngine.setEnabledProtocols(new String[] {"TLSv1.3"});

        ch.pipeline()
                .addLast(new SslHandler(sslEngine))
                // 根据模式设置HTTP编解码器
                .addLast(isServer ? new HttpServerCodec() : new io.netty.handler.codec.http.HttpClientCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebTransportHandler(observer));
    }
}