package io.netty.netty.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.net.URI;

/**
 * @author lxcecho 909231497@qq.com
 * @since 08.10.2021
 * <p>
 * 说明：
 * 1.SimpleChannelInboundHandler 是 ChannelInboundHandlerAdapter
 * 2.HttpObject 客户端和服务端相互通讯的数据被封装成 HttpObject
 */
public class TestHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    /*@Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
        // 判断 msg 是不是 httpRequest 请求
        if(httpObject instanceof HttpRequest){
            System.out.println("msg类型："+httpObject.getClass());
            System.out.println("客户端地址："+channelHandlerContext.channel().remoteAddress());
            
            // 回复信息给浏览器【http 协议】
            ByteBuf content = Unpooled.copiedBuffer("你好 我是服务器", CharsetUtil.UTF_8);

            // 构造一个 HTTP 的响应，即 httpResponse
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

            // 将构建好的 response 返回
            channelHandlerContext.writeAndFlush(response);
        }
    }*/

    /**
     * 读取客户端数据
     *
     * @param channelHandlerContext
     * @param httpObject
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
        System.out.println("对应的 channel = " + channelHandlerContext.channel()
                + " pipeline = " + channelHandlerContext.pipeline()
                + " 通过pipeline获取channel = " + channelHandlerContext.pipeline().channel());

        System.out.println("当前ctx的handler = " + channelHandlerContext.handler());

        //判断 msg 是不是 httpRequest 请求
        if (httpObject instanceof HttpRequest) {
            System.out.println("ctx 类型：" + channelHandlerContext.getClass());
            System.out.println("pipeline hashCode：" + channelHandlerContext.pipeline().hashCode()
                    + " TestHttpServerHandler hash:" + this.hashCode());
            System.out.println("httpObject 类型：" + httpObject.getClass());
            System.out.println("客户端地址：" + channelHandlerContext.channel().remoteAddress());

            HttpRequest httpRequest = (HttpRequest) httpObject;
            // 获取到 uri，过滤掉指定资源
            URI uri = new URI(httpRequest.uri());
            if ("/favicon.ico".equals(uri.getPath())) {
                System.out.println("请求了 favicon.ico，不做响应");
                return;
            }
            // 回复信息给浏览器，【http协议】
            ByteBuf context = Unpooled.copiedBuffer("Hello, I'm Server.", CharsetUtil.UTF_8);
            // 构建一个 http 的响应，即 httpResponse
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, context);

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, context.readableBytes());

            // 将都建好 response 返回
            channelHandlerContext.writeAndFlush(response);
        }
    }


}
