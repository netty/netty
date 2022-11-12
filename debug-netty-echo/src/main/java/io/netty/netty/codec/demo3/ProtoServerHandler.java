package io.netty.netty.codec.demo3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:06 10-11-2022
 */
public class ProtoServerHandler extends SimpleChannelInboundHandler<DataInfo.MyMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DataInfo.MyMessage msg) throws Exception {
        DataInfo.MyMessage.DataType dataType = msg.getDataType();
        if (DataInfo.MyMessage.DataType.PersonType == dataType) {
            DataInfo.Person person = msg.getPerson();
            System.out.println(person.getName() + " " + person.getAddress() + " " + person.getAge());
        } else if (DataInfo.MyMessage.DataType.DogType == dataType) {
            DataInfo.Dog dog = msg.getDog();
            System.out.println(dog.getName() + " " + dog.getAge());
        } else if (DataInfo.MyMessage.DataType.CatType == dataType) {
            DataInfo.Cat cat = msg.getCat();
            System.out.println(cat.getName() + " " + cat.getAge());
        }
    }
}
