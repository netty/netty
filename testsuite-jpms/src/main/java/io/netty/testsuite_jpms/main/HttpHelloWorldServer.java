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

package io.netty.testsuite_jpms.main;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.Channel;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.IdentityCipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;

import static io.netty.handler.ssl.SslContextBuilder.forServer;

/**
 * <p>An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.</p>
 *
 * <p>Running the server:
 * <ul>
 *     <li>./target/maven-jlink/default/bin/java
 *     -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer</li>
 *     <li>./target/maven-jlink/default/bin/http (shortcut)</li>
 * </ul>
 *
 * <p>Running with OpenSSL requires to add the
 * io.netty.internal.tcnative.openssl.${os.detected.name}.${os.detected.arch} module, e.g.
 * ./target/maven-jlink/default/bin/java --add-modules io.netty.internal.tcnative.openssl.osx.aarch_64
 * -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --ssl --ssl-provider OPENSSL
 *
 * <p>Running with native requires to add io.netty.transport.kqueue.${os.detected.name}.${os.detected.arch}, e.g.
 * ./target/maven-jlink/default/bin/java --add-modules io.netty.transport.kqueue.osx.aarch_64
 * -m io.netty.testsuite_jpms.main/io.netty.testsuite_jpms.main.HttpHelloWorldServer --transport kqueue
 */
public final class HttpHelloWorldServer {

    private HttpHelloWorldServer() {
    }

    public static void main(String[] args) throws Exception {

        String transport = "nio";
        boolean ssl = false;
        SslProvider sslProvider = SslProvider.JDK;

        Integer port = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) {
                System.out.println("usage: [options]");
                System.out.println("--ssl");
                System.out.println("--ssl-provider [ JDK | OPENSSL ]");
                System.out.println("--port <port>");
                System.out.println("--transport [ nio | kqueue | epoll | io_uring ]");
                System.exit(0);
            }
            if (args[i].equals("--ssl")) {
                ssl = true;
            }
            if (args[i].equals("--ssl-provider")) {
                if (i < args.length - 1) {
                    sslProvider = SslProvider.valueOf(args[++i]);
                } else {
                    System.exit(1);
                }
            }
            if (args[i].equals("--port")) {
                if (i < args.length - 1) {
                    port = Integer.parseInt(args[++i]);
                } else {
                    System.exit(1);
                }
            }
            if (args[i].equals("--transport")) {
                if (i < args.length - 1) {
                    transport = args[++i];
                } else {
                    System.exit(1);
                }
            }
        }

        if (port == null) {
            port = ssl ? 8443 : 8080;
        }

        IoHandlerFactory ioHandlerFactory;
        Class<? extends ServerSocketChannel> serverSocketChannelFactory;
        switch (transport) {
            case "nio":
                ioHandlerFactory = NioIoHandler.newFactory();
                serverSocketChannelFactory = NioServerSocketChannel.class;
                break;
            case "kqueue":
                ioHandlerFactory = KQueueIoHandler.newFactory();
                serverSocketChannelFactory = KQueueServerSocketChannel.class;
                break;
            case "epoll":
                ioHandlerFactory = EpollIoHandler.newFactory();
                serverSocketChannelFactory = EpollServerSocketChannel.class;
                break;
            case "io_uring":
                ioHandlerFactory = IoUringIoHandler.newFactory();
                serverSocketChannelFactory = IoUringServerSocketChannel.class;
                break;
            default:
                System.exit(1);
                return;
        }

        X509Bundle cert = new CertificateBuilder()
                .subject("cn=localhost")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        SslContext sslContext;
        if (ssl) {
            sslContext = forServer(cert.toKeyManagerFactory())
                    .sslProvider(sslProvider)
                    .protocols("TLSv1.2")
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .ciphers(null, IdentityCipherSuiteFilter.INSTANCE)
                    .sessionCacheSize(0)
                    .sessionTimeout(0)
                    .build();
        } else {
            sslContext = null;
        }

        // Configure the server.
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, ioHandlerFactory);
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(ioHandlerFactory);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
             .channel(serverSocketChannelFactory)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new HttpHelloWorldServerInitializer(sslContext));

            Channel ch = b.bind(port).sync().channel();

            System.err.println("Open your web browser and navigate to " +
                    (ssl? "https" : "http") + "://127.0.0.1:" + port + '/');

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
