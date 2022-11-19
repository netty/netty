package io.netty.netty.simple.demo2;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.10.2021
 */
public class NettyServer {

    public static void main(String[] ars) {
        // 创建 BossGroup 和 WorkerGroup
        // 1 创建两个线程组 bossGroup 和 workerGroup
        // 2 bossGroup 只是处理连接的请求，真正和客户端业务处理，会交给 workerGroup 完成
        // 3 两个都是无限循环
        // 4 boosGroup 和 workerGroup 含有的子线程（NioEventLoop）的个数
        // 默认实际 cpu 核数*2
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 创建服务器端的启动对象，配置参数
            ServerBootstrap bootstrap = new ServerBootstrap();

            // 使用链式编程来进行设置
            bootstrap.group(bossGroup, workerGroup) // 设置两个线程组
                    .channel(NioServerSocketChannel.class) // 使用 NioSocketChannel 作为服务器的通道实现
                    .option(ChannelOption.SO_BACKLOG, 128) // 设置线程队列得到的连接数
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // 设置保持活动连接状态
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 创建一个通道测试对象（匿名对象）
                        /**
                         * 给 pipeline 设置处理器
                         * @param socketChannel
                         * @throws Exception
                         */
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            // 可以使用一个集合管理 SocketChannel，在推送消息时，可以将业务加入到各个 channel 对应的 NIOEventLoop 的taskQueue 或 scheduleTaskQueue
                            System.out.println("客户端 socketChannel hashCode = "+socketChannel.hashCode());
                            socketChannel.pipeline().addLast(new NettyServerHandler());
                        }
                    }); // 给我们的 workerGroup 的 EventLoop 对应的管道设置处理器

            System.out.println("....server is ready....");

            // 绑定一个端口并且同步，生成了一个 ChannelFuture 对象
            // 异步地绑定服务器（并绑定端口）；调用 sync() 方法阻塞等待直到绑定完成
            ChannelFuture channelFuture = bootstrap.bind(6668).sync();

            // 给 cf 注册监听器，监控我们关心的事件
            channelFuture.addListener(new ChannelFutureListener(){ // 注册一个 ChannelFutureListener，以便在操作完成时获得通知
                @Override
                public void operationComplete(ChannelFuture future) throws Exception{
                    // 如果是操作成功的，输出一句话
                    if(channelFuture.isSuccess()) { // 检查操作的状态
                        System.out.println("监听端口 6668 成功");
                    } else {
                        // 如果失败，则打印日志
                        System.out.println("监听端口 6668 失败");
                    }
                }
            });

            // 对关闭通道进行监听，即获取 Channel 的 CloseFuture，并且阻塞当前线程直到它完成
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
