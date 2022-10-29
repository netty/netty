package io.netty.netty.tomcat.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10:35 29-10-2022
 */
public class NettyResponse {

    private ChannelHandlerContext ctx;

    private HttpRequest req;

    public NettyResponse(ChannelHandlerContext ctx, HttpRequest req) {
        this.ctx = ctx;
        this.req = req;
    }

    public void write(String out) throws Exception {
        try {
            if (out == null || out.length() == 0) {
                return;
            }
            // 设置 http协议及请求头信息
            FullHttpResponse response = new DefaultFullHttpResponse(
                    // 设置 http 版本为 1.1
                    HttpVersion.HTTP_1_1,
                    // 设置响应状态码
                    HttpResponseStatus.OK,
                    // 将输出值写出 编码为 UTF-8
                    Unpooled.wrappedBuffer(out.getBytes(StandardCharsets.UTF_8)));
            response.headers().set("Content-Type", "text/html;");
            ctx.write(response);
        } finally {
            ctx.flush();
            ctx.close();
        }
    }

}
