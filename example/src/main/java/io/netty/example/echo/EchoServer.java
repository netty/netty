/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * 1. 源码需要剖析到 Netty 调用 doBind() 方法，追踪到 NioServerSocketChannel 的 doBind();
 * 2. 并且要 Debug 程序到 NioEventLoop 类的 run() 代码，无限循环，在服务器端运行
 *
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    // 创建业务线程池
    // 这里我们就创建 2 个子线程
    static final EventExecutorGroup group = new DefaultEventExecutorGroup(2);

    public static void main(String[] args) throws Exception {
        // Configure SSL. 创建了 SSL 的配置类
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server. 重点分析：创建了两个 EventLoopGroup 对象，可以说，整个 Netty 的运作都依赖他们。
        /**
         * 1. 用于接受 TCP 请求，他会将请求交给 workerGroup，workerGroup 会获取到真正的连接，然后和连接进行通信，比如读写解码编码等操作。
         * 2. EventLoopGroup 是事件循环组（线程组），含有多个 EventLoop，可以注册 channel，用于在事件循环中去进行选择（和选择器相关）。
         * 3. new NioEventLoopGroup(1); 这个 1 表示 bossGroup 事件组有一个线程你可以指定，如果 new NioEventLoopGroup(); 会含有默认个线程【cpu 核数*2】，即可以充分的利用多核的优势。
         *  io.netty.channel.MultithreadEventLoopGroup#DEFAULT_EVENT_LOOP_THREADS
         *  DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
         *                  "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
         * 4. 会创建 EventExecutor 数组：children = new EventExecutor[nThreads];
         * 5. 每个元素类型就是 NioEventLoop，NioEventLoop 实现了 EventLoop 接口和 Executor 接口，try 块中
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            /**
             * 6. 创建了一个 ServerBootStrap 对象，它是一个引导类，用于启动服务器和引导整个程序的初始化。他和 ServerChannel 关联，而 ServerChannel 继承了 Channel，有一些方法 remoteAddress 等。
             * 7. 然后添加了一个 Channel，其中参数是一个 Class 对象，引导类将通过这个 Class 对象反射创建 ChannelFactory，然后添加了一些 TCP 的参数。【说明：Channel 的创建在 bind 方法，可以 debug
             *  该方法，会找到 channel = channelFactory.newChannel();】
             * 8. 再添加了一个服务器专属的日志处理器 handler；
             * 9. 再添加一个 SocketChannel（不是 ServerSocketChannel）的 handler；
             * 10. 然后绑定端口并阻塞至连接成功；
             * 11. 最后 main 线程阻塞等待关闭；
             * 12. finally 块中的代码将在服务器关闭时优雅关闭所有资源。
             */
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
                    // 传入 TCP 参数，放在一个 LinkedHashMap 中
             .option(ChannelOption.SO_BACKLOG, 100)
                    // handler 只属于 ServerSocketChannel，而不是 SocketChannel
             .handler(new LoggingHandler(LogLevel.INFO))
                    // childHandler 将会在每个客户端连接的时候调用，供 SocketChannel 使用
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(serverHandler);
                     // 说明: 如果我们在 addLast 添加 handler ，前面有指定
                     // EventExecutorGroup, 那么该 handler 会优先加入到该线程池中
                 }
             });

            // Start the server. 服务器就是在这个 bind() 方法里启动完成的
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
