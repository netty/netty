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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.POST;

/**
 * A SockJS server that will start the services required for the
 * <a href="http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html">sockjs-protocol</a> test suite,
 * enabling the python test suite to be run against Netty's SockJS implementation.
 *
 * <h3>Running the sockjs-protcol-0.3.3 testsuite</h3>
 * <p>
 *  One of the changes when doing the refactoring was to use Netty's {@link CorsHandler} which was extracted
 *  and generalized. Doing this caused a few errors to be returned by sockjs-protocol 0.3.3 tests as a few of
 *  the tests CORS handling is not correct in my opinion. The following pull requests have been registered for this:
 *  <ul>
 *     <li>https://github.com/sockjs/sockjs-protocol/pull/79</li>
 *     <li>https://github.com/sockjs/sockjs-protocol/pull/77</li>
 *  </ul>
 *
 * These two are included in this <a href="https://github.com/danbev/sockjs-protocol/tree/netty-fixes">branch</a> and
 * can be used to run the sockjs-protocol testsuite.
 * <ol>
 *     <li>Start the Netty SockJS Server</li>
 *          cd netty/codec-sockjs <br>
 *          mvn exec:java
 *     <li>Run <a href="http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html">sockjs-protocol</a></li>
 *         cd sockjs-protocol <br>
 *         make test_deps (only required to be run once) <br>
 *         ./venv/bin/python sockjs-protocol-0.3.3.py
 * </ol>
 * <h3>HAProxy test failure</h3>
 * Currently the haproxy test in the does not pass. The issue here is that when HAProxy tries to send an WebSocket
 * Hixie 76 upgrade reqeust it need to be able to send the request headers, and receive the response before it
 * sends the actualy nouce. The test, test_haproxy, and I'm assuming HAProxy itself does not set a Content-Length
 * header. HttpObjectDecoder in Netty does a check while decoding to see if a Content-Lenght header exists, which
 * is our case it does not. It then  does a second check to see if the request is a WebSocket request, and correctly
 * detects that this is. Netty will then set the Content-Lenght header to 8 for Hixie 76. This causes the request to
 * not be passed along as there is not actual body in this request.
 * <p>
 * Setting the Content-Length to 0 allows the test_haproxy test to pass successfully. I'm not sure
 * how to fix this. I don't think that there should really be a case where the body of a Hixie 74 upgrade request
 * does not have the nounce in the body of the request. So perhaps a workaround specific to sockjs should be put
 * inplace.
 */
public class NettySockJsServer {

    private final int port;

    public NettySockJsServer(final int port) {
        this.port = port;
    }

    public void run() throws Exception {
        final EventLoopGroup bossGroup = new NioEventLoopGroup();
        final EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            final SockJsServiceFactory echoService = echoService();
            final SockJsServiceFactory wsDisabled = wsDisabledService();
            final SockJsServiceFactory closeService = closeService();
            final SockJsServiceFactory cookieNeededService = cookieService();
            final ServerBootstrap sb = new ServerBootstrap().channel(NioServerSocketChannel.class);
            final CorsConfig corsConfig = SockJsChannelInitializer.defaultCorsOptions("test", "*", "localhost:8081")
                    .allowedRequestHeaders("a", "b", "c")
                    .allowNullOrigin()
                    .allowedRequestMethods(POST, GET, OPTIONS)
                    .build();

            final SockJsChannelInitializer chInit = new SockJsChannelInitializer(corsConfig, echoService,
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

    private static SockJsServiceFactory echoService() {
        final SockJsConfig config = SockJsConfig.withPrefix("/echo")
                .cookiesNeeded()
                .heartbeatInterval(25000)
                .sessionTimeout(5000)
                .maxStreamingBytesSize(4096)
                .build();
        return new AbstractSockJsServiceFactory(config) {
            @Override
            public SockJsService create() {
                return new EchoService(config);
            }
        };
    }

    private static SockJsServiceFactory wsDisabledService() {
        final SockJsConfig config = SockJsConfig.withPrefix("/disabled_websocket_echo").disableWebSocket().build();
        return new AbstractSockJsServiceFactory(config) {
            @Override
            public SockJsService create() {
                return new EchoService(config);
            }
        };
    }

    private static SockJsServiceFactory closeService() {
        final SockJsConfig config = SockJsConfig.withPrefix("/close").build();
        return new AbstractSockJsServiceFactory(config) {
            @Override
            public SockJsService create() {
                return new CloseService(config);
            }
        };
    }

    private static SockJsServiceFactory cookieService() {
        final SockJsConfig config = SockJsConfig.withPrefix("/cookie_needed_echo").cookiesNeeded().build();
        return new AbstractSockJsServiceFactory(config) {
            @Override
            public SockJsService create() {
                return new CloseService(config);
            }
        };
    }

    public static void main(final String[] args) throws Exception {
        final int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        new NettySockJsServer(port).run();
    }

}
