package io.netty.netty.codec;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;

import java.nio.charset.StandardCharsets;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10:49 06-08-2022
 */
public class DecoderTest {

    public static void main(String[] args) throws Exception {
        /**
         * 使用 telnet 测试
         * FixedLengthFrameDecoder：
         *  telnet localhost 8088
         *  Ctrl ]
         *  sen 1234567890123
         *  sen 456789012
         *
         * DelimiterBasedFrameDecoder：
         *  sen hello&world&1234567890ab
         */
        new DecoderTest().startEchoServer(8088);
    }

    public void startEchoServer(int port) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            /*FixedLengthFrameDecoder 固定长度解码器*/
            /*serverBootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(port)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new FixedLengthFrameDecoder(10))
                                    .addLast(new EchoServer());
                        }
                    });*/

            /*特殊分隔符解码器 DelimiterBasedFrameDecoder*/
            serverBootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(port)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ByteBuf delimiter = Unpooled.copiedBuffer("&".getBytes());
                            ch.pipeline()
                                    /**
                                     *  public DelimiterBasedFrameDecoder(int maxFrameLength, boolean stripDelimiter, boolean failFast, ByteBuf delimiter) {
                                     *  maxFrameLength  报文最大长度的限制。如果超过 maxLength 还没有检测到指定分隔符，将会抛出 TooLongFrameException。
                                     *  stripDelimiter  stripDelimiter 的作用是判断解码后得到的消息是否去除分隔符。
                                     *  failFast    与 maxLength 需要搭配使用，通过设置 failFast 可以控制抛出 TooLongFrameException 的时机，
                                     *              如果 failFast=true，那么在超出 maxLength 会立即抛出 TooLongFrameException，不再继续进行解码。
                                     *              如果 failFast=false，那么会等到解码出一个完整的消息后才会抛出 TooLongFrameException。
                                     *  delimiter   指定特殊分隔符，通过写入 ByteBuf 作为参数传入。
                                     */
                                    .addLast(new DelimiterBasedFrameDecoder(10,true, true, delimiter))
                                    .addLast(new EchoServer());
                        }
                    });
            ChannelFuture channelFuture = serverBootstrap.bind().sync();
            channelFuture.channel().closeFuture().sync();
        } finally {
            worker.shutdownGracefully();
            boss.shutdownGracefully();
        }
    }

    @ChannelHandler.Sharable
    public static class EchoServer extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("Receive client: [" + ((ByteBuf) msg).toString(StandardCharsets.UTF_8) + "]");
        }
    }

}
