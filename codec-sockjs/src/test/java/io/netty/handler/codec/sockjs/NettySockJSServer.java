/*
 * Copyright 2012 The Netty Project
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class NettySockJSServer {

    private final int port;

    public NettySockJSServer(final int port) {
        this.port = port;
    }

    public void run() throws Exception {
        final EventLoopGroup bossGroup = new NioEventLoopGroup();
        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            final SockJSServiceFactory echoService = echoService();
            final SockJSServiceFactory wsDisabled = wsDisabledService();
            final SockJSServiceFactory closeService = closeService();
            final SockJSServiceFactory cookieNeededService = cookieService();
            final ServerBootstrap sb = new ServerBootstrap().channel(NioServerSocketChannel.class);
            final SockJSChannelInitializer chInit = new SockJSChannelInitializer(echoService,
                    wsDisabled,
                    closeService,
                    cookieNeededService);
            sb.group(bossGroup, workerGroup).childHandler(chInit);
            final Channel ch = sb.bind(port).sync().channel();
            System.out.println("Web socket server started on port [" + port + "], ");
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static SockJSServiceFactory echoService() {
        final Config config = Config.prefix("/echo")
                .cookiesNeeded()
                .heartbeatInterval(25000)
                .sessionTimeout(5000)
                .maxStreamingBytesSize(4096)
                .build();
        return new AbstractServiceFactory(config) {
            @Override
            public SockJSService create() {
                return new EchoService(config);
            }
        };
    }

    private static SockJSServiceFactory wsDisabledService() {
        final Config config = Config.prefix("/disabled_websocket_echo").disableWebsocket().build();
        return new AbstractServiceFactory(config) {
            @Override
            public SockJSService create() {
                return new EchoService(config);
            }
        };
    }

    private static SockJSServiceFactory closeService() {
        final Config config = Config.prefix("/close").build();
        return new AbstractServiceFactory(config) {
            @Override
            public SockJSService create() {
                return new CloseService(config);
            }
        };
    }

    private static SockJSServiceFactory cookieService() {
        final Config config = Config.prefix("/cookie_needed_echo").cookiesNeeded().build();
        return new AbstractServiceFactory(config) {
            @Override
            public SockJSService create() {
                return new CloseService(config);
            }
        };
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8090;
        new NettySockJSServer(port).run();
    }

}
