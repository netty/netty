package io.netty.netty.codec.demo3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Random;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:06 10-11-2022
 */
public class ProtoClientHandler extends SimpleChannelInboundHandler<DataInfo.MyMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DataInfo.MyMessage msg) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        int n = (int) (1 + Math.random() * (3 - 1 + 1));
        System.out.println("n: " + n);
        DataInfo.MyMessage myMessage = DataInfo.MyMessage.newBuilder().build();
        if (0 == n) {
            myMessage = DataInfo.MyMessage.newBuilder()
                    .setDataType(DataInfo.MyMessage.DataType.PersonType)
                    .setPerson(DataInfo.Person.newBuilder().setName("lxceco").setAge(18).setAddress("广西钦州").build())
                    .build();
        } else if (1 == n) {
            myMessage = DataInfo.MyMessage.newBuilder()
                    .setDataType(DataInfo.MyMessage.DataType.DogType)
                    .setDog(DataInfo.Dog.newBuilder().setName("Heman").setAge(18).build())
                    .build();
        } else if (2 == n) {
            myMessage = DataInfo.MyMessage.newBuilder()
                    .setDataType(DataInfo.MyMessage.DataType.CatType)
                    .setCat(DataInfo.Cat.newBuilder().setName("Eman").setAge(18).build())
                    .build();
        }
        ctx.channel().writeAndFlush(myMessage);
    }
}
