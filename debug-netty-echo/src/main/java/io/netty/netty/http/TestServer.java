package io.netty.netty.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 08.10.2021
 */
public class TestServer {
    public static void main(String[] args) {
        /**
         * 事件循环组，就是死循环
         */
        // 仅仅接收连接，转给 workerGroup，自己不做处理
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        // 真正处理
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 很轻松的启动服务端代码
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            // childHandler 子处理器，传入一个初始化器参数 TestServerInitializer（自定义）
            // TestServerInitializer 在 Channel 被注册时，就会创建调用
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    /*.handler(null) */// 该 handler 对应 bossGroup，childHandler 对应 workerGroup
                    .childHandler(new TestServerInitializer());
            // 绑定一个端口并且同步，生成一个 ChannelFuture 对象
            ChannelFuture channelFuture = serverBootstrap.bind(6669).sync();
            // 对关闭的监听
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 循环组优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
