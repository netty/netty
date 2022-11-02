package io.netty.netty.inoroutboundhandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * @author lxcecho 909231497@qq.com
 * @since 12.12.2021
 */
public class MyByteToLongDecoder extends ByteToMessageDecoder {
    /**
     * decode 会根据接收的数据，被调用多次，直到确定没有新的元素被添加到 list，
     * 或者是 ByteBuf 没有更多的可读字节为止。
     * 如果 list out 不为空，就会将 list 的内容传递给下一个 channelInboundHandler 处理，该处理器的方法也会被调用多次。
     *
     * @param ctx
     * @param in
     * @param out
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        System.out.println("MyByteToLongDecoder 被调用");
        if(in.readableBytes() >= 8) {
            // 因为 long 8个字节，需要判断有8个字节，才能读取一个long
            out.add(in.readLong());
        }
    }
}