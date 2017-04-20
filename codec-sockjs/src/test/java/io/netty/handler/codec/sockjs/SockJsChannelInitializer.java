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
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.sockjs.handler.SockJsHandler;
import io.netty.handler.codec.sockjs.util.Arguments;

import java.util.Date;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;

/**
 * {@link ChannelInitializer} for Sockjs.
 *
 */
public class SockJsChannelInitializer extends ChannelInitializer<Channel> {

    private final SockJsServiceFactory[] services;
    private final CorsConfig corsConfig;

    public SockJsChannelInitializer(final CorsConfig corsConfig, final SockJsServiceFactory... services) {
        Arguments.checkNotNull(corsConfig, "corsConfig");
        Arguments.checkNotNull(services, "services");
        this.corsConfig = corsConfig;
        this.services = services;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("chunkAggregator", new HttpObjectAggregator(130 * 1024));
        pipeline.addLast("cors", new CorsHandler(corsConfig));
        pipeline.addLast("sockjs", new SockJsHandler(services));
    }

    /**
     * Returns a {@link CorsConfigBuilder} that is pre configured with SockJS default
     * CORS options.
     *
     * @param origins The supported origins that for CORS.
     * @return {@link CorsConfigBuilder} with the default SockJS configuration which can be futher configured
     */
    public static CorsConfigBuilder defaultCorsOptions(final String... origins) {
        return setCorsDefaults(CorsConfigBuilder.forOrigins(origins));
    }

    /**
     * Returns a {@link CorsConfigBuilder} that is pre configured with SockJS default
     * CORS options.
     *
     * @return {@link CorsConfigBuilder} with the default SockJS configuration which can be futher configured
     */
    public static CorsConfigBuilder defaultCorsOptions() {
        return setCorsDefaults(CorsConfigBuilder.forAnyOrigin());
    }

    private static CorsConfigBuilder setCorsDefaults(final CorsConfigBuilder b) {
        return b.allowCredentials()
                .preflightResponseHeader(CACHE_CONTROL, "public, max-age=31536000")
                .preflightResponseHeader(SET_COOKIE, "JSESSIONID=dummy;path=/")
                .preflightResponseHeader(EXPIRES, new Callable<Date>() {
                    @Override
                    public Date call() throws Exception {
                        final Date date = new Date();
                        date.setTime(date.getTime() + 3600 * 1000);
                        return date;
                    }
                })
                .maxAge(31536000);
    }
}
