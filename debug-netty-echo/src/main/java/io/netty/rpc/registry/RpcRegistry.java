package io.netty.rpc.registry;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

/**
 * 负责将所有 Provider 的服务名称和服务引用地址注册到一个容器中，并对外发布
 * Registry 要启动一个对外的服务，很显然应该作为服务端，并提供一个对外可以访问的端口
 *
 * @author lxcecho 909231497@qq.com
 * @since 16:30 29-10-2022
 */
public class RpcRegistry {

    private int port;

    public RpcRegistry(int port) {
        this.port = port;
    }

    public void start() {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    /**
                                     * 自定义协议解码器， 入参有 5 个
                                     *  int maxFrameLength, 框架的最大长度，如果帧的长度大于此值，则将抛出 TooLongFrameException
                                     *  int lengthFieldOffset, 长度属性的偏移量，即对应的长度属性在整个消息数据中的位置
                                     *  int lengthFieldLength, 长度字段的长度，如果长度属性是 int 类型，那么这个值就是 4（long 就是8（
                                     *  int lengthAdjustment, 要添加到长度属性值的补偿值
                                     *  int initialBytesToStrip 从解码帧中去除的第一个字节数
                                     */
//                                    .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
//                                    // 自定义协议编码器
//                                    .addLast(new LengthFieldPrepender(4))
                                    // 对象参数类型编码器
                                    .addLast("encoder", new ObjectEncoder())
                                    // 对象类型解码器
                                    .addLast("decoder", new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)))
                                    .addLast(new RegistryHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = serverBootstrap.bind(port).sync();
            System.out.println("Netty RPC Registry start listen at " + port);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new RpcRegistry(8090).start();
    }

}
