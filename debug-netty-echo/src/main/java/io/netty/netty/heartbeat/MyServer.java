package io.netty.netty.heartbeat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * @author lxcecho 909231497@qq.com
 * @since 12.12.2021
 */
public class MyServer {

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            // 加入一个 netty 提供 IdleStateHandler
                            /**
                             * 说明：IdleStateHandler 是 netty 提供的处理空闲状态的处理器
                             * long readerIdleTime, 表示多长时间没有读，就会发送一个心跳检测包检测是否连接
                             * long writerIdleTime, 表示多长时间没有写，就会发送一个心跳检测包检测是否连接
                             * long allIdleTime, 表示多长时间没有读写，就会发送要给心跳检测包检测是否连接
                             * 文档说明：
                             * Triggers an IdleStateEvent when a Channel has not performed read, write, or both operation for a while.
                             * Supported idle states
                             * 当 IdleStateEvent 触发后，就会传递给管道的下一个 handler 去处理
                             * 通过调用（触发）下一个 handler 的 userEventTriggered，在该方法中去处理 IdleStateEvent（读空闲，写空闲，读写空闲）
                             */
//                            pipeline.addLast(new IdleStateHandler(3, 5, 7, TimeUnit.SECONDS));
                            pipeline.addLast(new IdleStateHandler(13, 5, 2, TimeUnit.SECONDS));
                            // 加入一个对空闲检测进一步处理的 handler（自定义）
                            pipeline.addLast(new MyServerHandler());
                        }
                    });

            ChannelFuture channelFuture = bootstrap.bind(7000).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    public static void main(String[] args) {
        new MyServer().run();
    }

}
