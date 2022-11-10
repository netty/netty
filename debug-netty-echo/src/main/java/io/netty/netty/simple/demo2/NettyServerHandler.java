package io.netty.netty.simple.demo2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.CharsetUtil;

import java.util.concurrent.TimeUnit;

/**
 * @author lxcecho 909231497@qq.com
 * @since 03.10.2021
 * <p>
 * 1.自定义一个 Handler 需要继承 netty 规定好的某个 HandlerAdapter（规范）
 * 2.这时自定义的 Handler，才能称为一个 Handler
 */
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 读取数据事件（这里可以读取客户端发送的消息）
     * 1、ChannelHandlerContext ctx：上下文对象，含有管道 pipeline，通道 channel，地址
     * 2、Object msg：就是客户端发送的数据，默认 Object
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("服务器读取线程" + Thread.currentThread().getName());
        System.out.println("server ctx = " + ctx);
        System.out.println("看看 channel 和 pipeline 的关系");
        Channel channel = ctx.channel();
        ChannelPipeline pipeline = ctx.pipeline(); // 本质是一个双向链接，出站入站

        // 将 msg 转成一个 ByteBuffer
        // ByteBuffer 是 Netty 提供的，不是 NIO 的 ByteBuffer
        ByteBuf buf = (ByteBuf) msg;
        System.out.println("客户端发送消息是：" + buf.toString(CharsetUtil.UTF_8));
        System.out.println("客户端地址：" + channel.remoteAddress());

        // 比如这里有一个非常耗时的业务-->异步执行-->提交该 channel 对应的 NioEventLoop 的 taskQueue 中
        // todo 方案一：用户程序自定义普通任务
        /*ctx.channel().eventLoop().execute(() -> {
            try {
                Thread.sleep(5 * 1000);
                ctx.writeAndFlush(Unpooled.copiedBuffer("hello, client.", CharsetUtil.UTF_8));
                System.out.println("channel code = " + ctx.channel().hashCode());
                System.out.println("go on...");
            } catch (Exception e) {
                System.out.println("发生异常：" + e.getMessage());
            }
        });*/

        // todo 方案二：用户自定义定时任务-->该任务是提交到 scheduledTaskQueue 中
        /*ctx.channel().eventLoop().schedule(() -> {
            try {
                Thread.sleep(5 * 1000);
                ctx.writeAndFlush(Unpooled.copiedBuffer("hello, client.", CharsetUtil.UTF_8));
                System.out.println("channel code = " + ctx.channel().hashCode());
            } catch (Exception e) {
                System.out.println("发生异常：" + e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);

        System.out.println("go on ...");*/

    }

    /**
     * 数据读取完毕
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // writeAndFlush 是 write + flush
        // 将数据写入到缓存，并刷新
        // 一般而言，我们对这个发送的数据进行编码
        ctx.writeAndFlush(Unpooled.copiedBuffer("Hello，客户端~~", CharsetUtil.UTF_8));
    }

    /**
     * 处理异常，一般是需要关闭通道
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }
}
