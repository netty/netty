package io.netty.netty.buf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

/**
 * @author lxcecho 909231497@qq.com
 * @since 10.10.2021
 */
public class NettyBufDemo02 {
    public static void main(String[] args) {
        // Charset utf8 = Charset.forName("utf-8");
        Charset utf8 = CharsetUtil.UTF_8;
        ByteBuf byteBuf = Unpooled.copiedBuffer("Hello, world!", utf8);

        if (byteBuf.hasArray()) {
            byte[] content = byteBuf.array();

            // 将 content 转成字符
            System.out.println(new String(content, utf8));

            System.out.println("byteBuf=" + byteBuf);

            System.out.println(byteBuf.arrayOffset()); // 0
            System.out.println(byteBuf.readerIndex()); // 0
            System.out.println(byteBuf.writerIndex()); // 12
            System.out.println(byteBuf.capacity()); // 36

            System.out.println(byteBuf.getByte(0)); // 104
//            System.out.println(byteBuf.readByte());

            // 可读字节数
            int len = byteBuf.readableBytes(); //12
            System.out.println("len=" + len);

            // 使用 for 取出各个字节
            for (int i = 0; i < len; i++) {
                System.out.println((char) byteBuf.getByte(i));
            }

            // 按照某个范围读取
            System.out.println(byteBuf.getCharSequence(0, 4, utf8));
            System.out.println(byteBuf.getCharSequence(4,6, utf8));

        }
    }
}
