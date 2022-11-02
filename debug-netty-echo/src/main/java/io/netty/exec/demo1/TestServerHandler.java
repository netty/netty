package io.netty.exec.demo1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.net.URI;

/**
 * @author lxcecho 909231497@qq.com
 * @since 28.02.2022
 * <p>
 * 自定义处理器（ChannelHandler）
 */
public class TestServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        HttpRequest request = (HttpRequest) msg;
        System.out.println(request.getMethod());

        URI uri = new URI(request.getUri());
        if ("/favicon/ico".equals(uri.getPath())) {
            System.out.println("请求 favicon.ico");
            return;
        }
        // 向客户端返回的内容
        ByteBuf byteBuf = Unpooled.copiedBuffer("Hello, Client.", CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());

        // 返回响应
        ctx.writeAndFlush(response);
        ctx.channel().close();
    }

    /**
     * 通道激活 —— 3
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel active...");
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel inactive...");
        super.channelInactive(ctx);
    }

    /**
     * 加入通道 —— 1
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handler added...");
        super.handlerAdded(ctx);
    }

    /**
     * 注册通道 —— 2
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel registered...");
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel unregistered...");
        super.channelUnregistered(ctx);
    }

    /**
     * handler added...
     * channel registered...
     * channel active...
     * channel inactive...
     * channel unregistered...
     */
}
