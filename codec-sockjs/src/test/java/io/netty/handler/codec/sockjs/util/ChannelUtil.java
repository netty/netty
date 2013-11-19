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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder;
import io.netty.handler.codec.sockjs.SockJsConfig;
import io.netty.handler.codec.sockjs.handler.CorsInboundHandler;
import io.netty.handler.codec.sockjs.handler.CorsOutboundHandler;
import io.netty.handler.codec.sockjs.handler.SockJsHandler;
import io.netty.handler.codec.sockjs.transport.WebSocketTransport;

public final class ChannelUtil {

    private ChannelUtil() {
    }

    public static EmbeddedChannel webSocketChannel(final SockJsConfig config) {
        return new EmbeddedChannel(
                new HttpServerCodec(),
                new CorsInboundHandler(),
                new CorsOutboundHandler(),
                new WebSocket13FrameEncoder(true),
                new WebSocket13FrameDecoder(true, false, 2048),
                new WebSocketTransport(config),
                new SockJsHandler());
    }

}
