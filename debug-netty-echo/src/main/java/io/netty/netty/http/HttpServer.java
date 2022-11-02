package io.netty.netty.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.net.InetSocketAddress;

/**
 * @author lxcecho 909231497@qq.com
 * @since 21:48 05-08-2022
 */
public class HttpServer {
    public void start(int port) throws Exception {

        // 主 Reactor：负责处理 Accept，然后把 Channel 注册到 从 Reactor 上
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        // 从 Reactor：负责 Channel 生命周期内的所有 IO 事件
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap serverBootstrap = new ServerBootstrap();

        try {
            serverBootstrap
                    // 1 配置线程池
                    .group(bossGroup, workerGroup)
                    // 2 Channel 初始化
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))
                    // 4 注册 Handler
                    /*ChannelInitializer：实现了 ChannelHandler 接口的匿名类，通过实例化 ChannelInitializer 作为 ServerBootStrap 的参数*/
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast("codec", new HttpServerCodec()) // HTTP 编解码器
                                    .addLast("compressor", new HttpContentCompressor()) // HttpContent 压缩
                                    .addLast("aggregator", new HttpObjectAggregator(65535)) // HTTP 消息聚合
                                    .addLast("handler", new HttpServerHandler()); // 自定义业务逻辑处理器
                        }
                    })
                    // 5 设置 Channel 参数，option：负责设置 Boss 线程组，childOption：负责设置 Worker 线程组。
                    /**
                     * SO_KEEPALIVE	设置为 true 代表启用了 TCP SO_KEEPALIVE 属性，TCP 会主动探测连接状态，即连接保活
                     * SO_BACKLOG	已完成三次握手的请求队列最大长度，同一时刻服务端可能会处理多个连接，在高并发海量连接的场景下，该参数应适当调大
                     * TCP_NODELAY	Netty 默认是 true，表示立即发送数据。如果设置为 false 表示启用 Nagle 算法，该算法会将 TCP 网络数据包累积到一定量才会发送，虽然可以减少报文发送的数量，但是会造成一定的数据延迟。Netty 为了最小化数据传输的延迟，默认禁用了 Nagle 算法
                     * SO_SNDBUF	TCP 数据发送缓冲区大小
                     * SO_RCVBUF	TCP数据接收缓冲区大小，TCP数据接收缓冲区大小
                     * SO_LINGER	设置延迟关闭的时间，等待缓冲区中的数据发送完成
                     * CONNECT_TIMEOUT_MILLIS	建立连接的超时时间
                     */
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            // 3 端口绑定
            ChannelFuture channelFuture = serverBootstrap.bind().sync();
            System.out.println("Http Server started, Listening on " + port);
            channelFuture.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new HttpServer().start(8088);
    }
}
