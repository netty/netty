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
import io.netty.handler.codec.http.cors.CorsConfig.Builder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.codec.sockjs.handler.SockJsHandler;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;

import java.util.Date;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpHeaders.Names.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaders.Names.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * {@link ChannelInitializer} for Sockjs.
 *
 */
public class SockJsChannelInitializer extends ChannelInitializer<Channel> {

    private final SockJsServiceFactory[] services;
    private final CorsConfig corsConfig;

    public SockJsChannelInitializer(final CorsConfig corsConfig, final SockJsServiceFactory... services) {
        ArgumentUtil.checkNotNull(corsConfig, "corsConfig");
        ArgumentUtil.checkNotNull(services, "services");
        this.corsConfig = corsConfig;
        this.services = services;
    }

    @Override
    protected void initChannel(final Channel channel) throws Exception {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("chunkAggregator", new HttpObjectAggregator(130 * 1024));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("cors", new CorsHandler(corsConfig));
        pipeline.addLast("sockjs", new SockJsHandler(services));
    }

    /**
     * Returns a {@link Builder} that is pre configured with SockJS default
     * CORS options.
     *
     * @param origins The supported origins that for CORS.
     * @return {@link Builder} with the default SockJS configuration which can be futher configured
     */
    public static Builder defaultCorsOptions(final String... origins) {
        return setCorsDefaults(new CorsConfig.Builder(origins));
    }

    /**
     * Returns a {@link Builder} that is pre configured with SockJS default
     * CORS options.
     *
     * @return {@link Builder} with the default SockJS configuration which can be futher configured
     */
    public static Builder defaultCorsOptions() {
        return setCorsDefaults(new CorsConfig.Builder());
    }

    private static Builder setCorsDefaults(final Builder b) {
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
