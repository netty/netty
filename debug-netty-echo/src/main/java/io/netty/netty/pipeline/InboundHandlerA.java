package io.netty.netty.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:45 30-10-2022
 */
public class InboundHandlerA extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("InboundHandler A");
        ctx.fireChannelRead(msg);
    }
}
