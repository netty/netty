package io.netty.exec.demo1;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 28.02.2022
 */
public class TestServer {
    public static void main(String[] args) {
        // 处理连接（线程组）
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // 处理业务逻辑（线程组）
        EventLoopGroup workerGroup = new NioEventLoopGroup(8);
        try {
            // 服务器
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new TestServerInitializerHandler());
            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
