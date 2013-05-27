/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.sockjs.SockJsChannelInitializer;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.SockJsService;
import io.netty.handler.codec.sockjs.SockJsServiceFactory;
import io.netty.handler.codec.sockjs.handler.SockJsHandler;
import io.netty.handler.codec.sockjs.transport.WebSocketTransport;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestChannels {

    private TestChannels() {
    }

    public static EmbeddedChannel webSocketChannel(final SockJsConfig config) {
        return new EmbeddedChannel(
                new HttpServerCodec(),
                new CorsHandler(corsConfig()),
                //new WebSocket13FrameEncoder(true),
                new WebSocket13FrameDecoder(true, false, 2048),
                new WebSocketTransport(config),
                new WsCodecRemover(),
                new SockJsHandler(serviceFactoryWithConfig(config)));
    }

    public static EmbeddedChannel webSocketChannel(final SockJsConfig config, final CorsConfig corsConfig) {
        return new TestEmbeddedChannel(
                new HttpServerCodec(),
                new CorsHandler(corsConfig),
                new WebSocket13FrameEncoder(true),
                new WebSocket13FrameDecoder(true, false, 2048),
                new WebSocketTransport(config),
                new SockJsHandler(serviceFactoryWithConfig(config)));
    }

    public static EmbeddedChannel webSocketChannel(final SockJsServiceFactory service, final CorsConfig corsConfig) {
        return new TestEmbeddedChannel(
                new HttpServerCodec(),
                new CorsHandler(corsConfig),
                new SockJsHandler(service));
    }

    public static EmbeddedChannel channelForService(final SockJsServiceFactory service) {
        return channelForService(service, corsConfig());
    }

    public static EmbeddedChannel channelForService(final SockJsServiceFactory service, final CorsConfig corsConfig) {
        return new TestEmbeddedChannel(
                new CorsHandler(corsConfig),
                new SockJsHandler(service));
    }

    public static EmbeddedChannel websocketChannelForService(final SockJsServiceFactory service) {
        return websocketChannelForService(service, corsConfig());
    }

    public static EmbeddedChannel websocketChannelForService(final SockJsServiceFactory service,
                                                             final CorsConfig corsConfig) {
        return new TestEmbeddedChannel(
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new CorsHandler(corsConfig),
                new SockJsHandler(service),
                new WsCodecRemover());
    }

    public static EmbeddedChannel jsonpChannelForService(final SockJsServiceFactory service) {
        return new TestEmbeddedChannel(new CorsHandler(corsConfig()),
                new SockJsHandler(service),
                new WsCodecRemover());
    }

    public static EmbeddedChannel channelForMockService(final SockJsConfig config) {
        final SockJsService service = mock(SockJsService.class);
        return channelForService(factoryForService(service, config), corsConfig());
    }

    public static EmbeddedChannel channelForMockService(final SockJsConfig config, final CorsConfig corsConfig) {
        final SockJsService service = mock(SockJsService.class);
        return channelForService(factoryForService(service, config), corsConfig);
    }

    public static SockJsServiceFactory factoryForService(final SockJsService service, final SockJsConfig config) {
        final SockJsServiceFactory factory = mock(SockJsServiceFactory.class);
        when(service.config()).thenReturn(config);
        when(factory.config()).thenReturn(config);
        when(factory.newService()).thenReturn(service);
        return factory;
    }

    public static CorsConfig corsConfig() {
        return corsConfigBuilder().build();
    }

    public static CorsConfig corsConfig(final String... origins) {
        return corsConfigBuilder(origins).build();
    }

    public static CorsConfigBuilder corsConfigBuilder() {
        return SockJsChannelInitializer.defaultCorsOptions()
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    }

    public static CorsConfigBuilder corsConfigBuilder(final String... origins) {
        return SockJsChannelInitializer.defaultCorsOptions(origins)
                .preflightResponseHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
    }

    private static class WsCodecRemover extends ChannelOutboundHandlerAdapter {

        private static final String WS_ENCODER = "wsencoder";

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg,
                          final ChannelPromise channelPromise) throws Exception {
            // Remove WebSocket encoder so that we can assert the plain WebSocketFrame
            if (ctx.pipeline().get(WS_ENCODER) != null) {
                ctx.pipeline().remove(WS_ENCODER);
            }
            ctx.pipeline().remove(this);
            ctx.writeAndFlush(msg, channelPromise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            super.close(ctx, promise);
        }
    }

    private static SockJsServiceFactory serviceFactoryWithConfig(final SockJsConfig config) {
        final SockJsServiceFactory serviceFactory = mock(SockJsServiceFactory.class);
        when(serviceFactory.config()).thenReturn(config);
        return serviceFactory;
    }
}
