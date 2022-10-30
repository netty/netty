package io.netty.netty.pipeline;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:48 30-10-2022
 */
public class PipelineServer {
    public void start(int port) throws Exception {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    // InboundHandler 的执行顺序注册顺序，应该是 A-B-C
                                    .addLast(new InboundHandlerA())
                                    .addLast(new InboundHandlerB())
                                    .addLast(new InboundHandlerC())
                                    // OutboundHandler 的执行顺序为注册顺序的逆序，应该是 C-B-A
                                    .addLast(new OutboundHandlerA())
                                    .addLast(new OutboundHandlerB())
                                    .addLast(new OutboundHandlerC());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
            channelFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new PipelineServer().start(8090);
    }

}
