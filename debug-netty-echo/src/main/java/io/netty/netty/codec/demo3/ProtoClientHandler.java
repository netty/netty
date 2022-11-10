package io.netty.netty.codec.demo3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:06 10-11-2022
 */
public class ProtoClientHandler extends SimpleChannelInboundHandler<PersonInfo.Person> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PersonInfo.Person msg) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        PersonInfo.Person person = PersonInfo.Person.newBuilder().setName("lxceco").setAge(18).setAddress("广西钦州").build();
        ctx.channel().writeAndFlush(person);
    }
}
