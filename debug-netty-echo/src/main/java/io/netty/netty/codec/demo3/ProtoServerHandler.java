package io.netty.netty.codec.demo3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:06 10-11-2022
 */
public class ProtoServerHandler extends SimpleChannelInboundHandler<PersonInfo.Person> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, PersonInfo.Person msg) throws Exception {
//        PersonInfo.Person person = msg;
        System.out.println(msg.getName() + " " + msg.getAddress() + " " + msg.getAge());
    }
}
