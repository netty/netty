/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.h2new;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

public final class Http2ServerSslContext extends DelegatingSslContext {

    private final ChannelInitializer<Channel> http1xPipelineInitializer;

    Http2ServerSslContext(SslContext ctx, ChannelInitializer<Channel> http1xPipelineInitializer) {
        super(ctx);
        this.http1xPipelineInitializer = http1xPipelineInitializer;
    }

    public boolean http1xAllowed() {
        return http1xPipelineInitializer != null;
    }

    public ChannelInitializer<Channel> http1xInitializer() {
        if (!http1xAllowed()) {
            throw new IllegalArgumentException("HTTP/1.x is not allowed");
        }
        return http1xPipelineInitializer;
    }

    @Override
    protected void initHandler(SslHandler handler) {
        super.initHandler(handler);
    }

    @Override
    protected void initEngine(SSLEngine engine) {
        // noop
    }

    ApplicationProtocolNegotiationHandler newApnHandler(ChannelInitializer<Channel> h2Initializer) {
        return new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ctx.pipeline().addLast(h2Initializer);
                } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol) && http1xAllowed()) {
                    ctx.pipeline().addLast(http1xInitializer());
                } else {
                    throw new IllegalStateException("unknown protocol: " + protocol);
                }
            }
        };
    }
}
