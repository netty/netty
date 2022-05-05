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

package io.netty5.handler.codec.h2new;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.handler.ssl.ApplicationProtocolNames;
import io.netty5.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty5.handler.ssl.DelegatingSslContext;
import io.netty5.handler.ssl.SslContext;

import javax.net.ssl.SSLEngine;

public final class Http2ClientSslContext extends DelegatingSslContext {

    Http2ClientSslContext(SslContext delegate) {
        super(delegate);
    }

    @Override
    protected void initEngine(SSLEngine engine) {
        // noop
    }

    ApplicationProtocolNegotiationHandler newApnHandler(ChannelInitializer<Channel> h2Initializer) {
        return new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ctx.pipeline().addLast(h2Initializer);
                } else {
                    throw new IllegalStateException("unknown protocol: " + protocol);
                }
            }
        };
    }
}
