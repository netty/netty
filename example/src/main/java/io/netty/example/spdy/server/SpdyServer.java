/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.spdy.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.spdy.SpdyOrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.Arrays;

/**
 * A SPDY Server that responds to a GET request with a Hello World.
 * <p>
 * This class must be run with the JVM parameter: {@code java -Xbootclasspath/p:<path_to_npn_boot_jar> ...}.
 * The "path_to_npn_boot_jar" is the path on the file system for the NPN Boot Jar file which can be downloaded from
 * Maven at coordinates org.mortbay.jetty.npn:npn-boot. Different versions applies to different OpenJDK versions.
 * See <a href="http://www.eclipse.org/jetty/documentation/current/npn-chapter.html">Jetty docs</a> for more
 * information.
 * <p>
 * You may also use the {@code run-example.sh} script to start the server from the command line:
 * <pre>
 *     ./run-example spdy-server
 * </pre>
 * <p>
 * Once started, you can test the server with your
 * <a href="http://en.wikipedia.org/wiki/SPDY#Browser_support_and_usage">SPDY enabled web browser</a> by navigating
 * to <a href="https://localhost:8443/">https://localhost:8443/</a>
 */
public class SpdyServer {

    private final SslContext sslCtx;
    private final int port;

    public SpdyServer(SslContext sslCtx, int port) {
        this.sslCtx = sslCtx;
        this.port = port;
    }

    public void run() throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new SpdyServerInitializer(sslCtx));

            Channel ch = b.bind(port).sync().channel();
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8443;
        }

        System.out.println("SPDY web server started at port " + port + '.');
        System.out.println("Open your SPDY enabled browser and navigate to https://localhost:" + port + '/');
        System.out.println("If using Chrome browser, check your SPDY sessions at chrome://net-internals/#spdy");

        // Configure SSL.
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslCtx = SslContext.newServerContext(
                ssc.certificate(), ssc.privateKey(), null, null,
                Arrays.asList(
                        SelectedProtocol.SPDY_3_1.protocolName(),
                        SelectedProtocol.HTTP_1_1.protocolName()),
                0, 0);

        new SpdyServer(sslCtx, port).run();
    }
}
