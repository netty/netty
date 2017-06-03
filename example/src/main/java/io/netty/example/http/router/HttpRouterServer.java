package io.netty.example.http.router;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.router.Router;

public class HttpRouterServer {
    public static int PORT = 8000;

    public static void main(String[] args) throws Exception {
        Router router = new Router()
            .GET("/",             new HttpRouterServerHandler())
            .GET("/articles/:id", HttpRouterServerHandler.class);
        System.out.println(router);

        NioEventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
         .childOption(ChannelOption.TCP_NODELAY,  java.lang.Boolean.TRUE)
         .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
         .channel(NioServerSocketChannel.class)
         .childHandler(new HttpRouterServerInitializer(router));

        Channel ch = b.bind(PORT).sync().channel();
        System.out.println("Server started: http://127.0.0.1:" + PORT + '/');
        ch.closeFuture().sync();

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
