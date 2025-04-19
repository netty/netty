/*
 * Copyright 2019 The Netty Project
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
package io.netty.testsuite.svm;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AdaptiveByteBufAllocator;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringBufferRingConfig;
import io.netty.channel.uring.IoUringFixedBufferRingAllocator;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringIoHandlerConfig;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public final class HttpNativeServer {

    /**
     * Main entry point (not instantiable)
     */
    private HttpNativeServer() {
    }

    public static void main(String[] args) throws Exception {
        for (TransportType value : TransportType.values()) {
            for (AllocatorType allocatorType : AllocatorType.values()) {
                boolean serverStartSucess = testTransport(value, allocatorType);
                System.out.println("Server started with transport type " + value + ": " + serverStartSucess);
                if (!serverStartSucess) {
                    System.exit(1);
                }
            }
        }
        // return the right system exit code to signal success
        System.exit(0);
    }

    public static boolean testTransport(TransportType ioType, AllocatorType allocatorType) throws Exception {
        // Configure the server.
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, chooseFactory(ioType));
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(chooseFactory(ioType));
        // Control status.
        boolean serverStartSucess = false;
        try {
            CompletableFuture<Void> httpRequestFuture = new CompletableFuture<>();
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
                    .channel(chooseServerChannelClass(ioType))
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childOption(ChannelOption.ALLOCATOR, chooseAllocator(allocatorType))
                    .childHandler(new HttpNativeServerInitializer(httpRequestFuture));

            Channel channel = b.bind(0).sync().channel();
            System.err.println("Server started, will shutdown now.");

            Channel httpClient = new HttpNativeClient(
                    ((InetSocketAddress) channel.localAddress()).getPort(),
                    workerGroup, chooseChannelClass(ioType)
            ).initClient();
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/hello");

            httpClient.writeAndFlush(request).sync();

            httpRequestFuture.get();

            channel.close().sync();
            httpClient.close().sync();
            serverStartSucess = true;
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
        return serverStartSucess;
    }

    public static IoHandlerFactory chooseFactory(TransportType ioType) {
        if (ioType == TransportType.EPOLL) {
            return EpollIoHandler.newFactory();
        }

        if (ioType == TransportType.IO_URING) {
            IoUringIoHandlerConfig config = new IoUringIoHandlerConfig();
            if (IoUring.isRegisterBufferRingSupported()) {
                config.setBufferRingConfig(new IoUringBufferRingConfig(
                        (short) 0, (short) 16, 16 * 16,
                        new IoUringFixedBufferRingAllocator(1024)
                ));
            }

           return IoUringIoHandler.newFactory(config);
        }

        return NioIoHandler.newFactory();
    }

    public static ByteBufAllocator chooseAllocator(AllocatorType allocatorType) {
        switch (allocatorType) {
            case POOLED : return PooledByteBufAllocator.DEFAULT;
            case UNPOOLED : return UnpooledByteBufAllocator.DEFAULT;
            case ADAPTIVE: return new AdaptiveByteBufAllocator();
            default: return PooledByteBufAllocator.DEFAULT;
        }
    }

    public static Class<? extends ServerSocketChannel> chooseServerChannelClass(TransportType ioType) {
        if (ioType == TransportType.EPOLL) {
            return EpollServerSocketChannel.class;
        }

        if (ioType == TransportType.IO_URING) {
            return IoUringServerSocketChannel.class;
        }

        return NioServerSocketChannel.class;
    }

    public static Class<? extends Channel> chooseChannelClass(TransportType ioType) {
        if (ioType == TransportType.EPOLL) {
            return EpollSocketChannel.class;
        }

        if (ioType == TransportType.IO_URING) {
            return IoUringSocketChannel.class;
        }

        return NioSocketChannel.class;
    }

    enum TransportType {
        NIO,
        EPOLL,
        IO_URING,
    }

    enum AllocatorType {
        POOLED,
        UNPOOLED,
        ADAPTIVE
    }
}
