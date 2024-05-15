/*
 * Copyright 2017 The Netty Project
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

package io.netty5.testsuite.http2;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.handler.logging.LogLevel;
import io.netty5.handler.logging.LoggingHandler;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * An HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 */
public final class Http2Server {

    private final int port;

    Http2Server(final int port) {
        this.port = port;
    }

    void run() throws Exception {
        // Configure the server.
        EventLoopGroup group = new MultithreadEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(group)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new Http2ServerInitializer());

            Channel ch = b.bind(port).asStage().get();

            ch.closeFuture().asStage().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 9000;
        }

        // We are supposed to be running with a classloader that presents us with the Maven test-classpath,
        // which we do: the h2spec-maven-plugin has set us up with a URLClassLoader that contain this classpath
        // in its list of URLs.
        // However, this classloader also has the Maven internal classloader as a parent, and that means some
        // dependencies, such as SLF4J, will be provided by the Maven process rather than our test-classpath.
        // Maven uses a different major version of SLF4J than we do, so this will cause us problems.
        // To fix this, we need to re-create the URLClassLoader *without* the current parent classloader.
        // That way, we will not have our class-world polluted by whatever stuff Maven is using.
        Class<Http2Server> serverClass = Http2Server.class;
        URLClassLoader classLoader = (URLClassLoader) serverClass.getClassLoader();
        URL[] urLs = classLoader.getURLs();
        try (URLClassLoader cl = new URLClassLoader(urLs)) { // Drop the parent classloader.
            Class<?> mainClass = cl.loadClass(serverClass.getName());
            Method start = mainClass.getMethod("start", int.class);
            start.invoke(null, port);
        }
    }

    @SuppressWarnings("unused")
    public static void start(int port) throws Exception {
        new Http2Server(port).run();
    }
}
