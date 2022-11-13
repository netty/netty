package io.netty.nio.buffer;

import java.nio.ByteBuffer;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 *
 * ByteBuffer 支持类型化的 put 和 get, put 放入的是什么数据类型，get 就应该使用相应的数据类型来取出，
 * 否则可能有 BufferUnderflowException 异常。
 */
public class ByteBufferPutGet {
    public static void main(String[] args) {
        // 创建一个 Buffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(64);

        // 类型化方式放入数据
        byteBuffer.putInt(100);
        byteBuffer.putLong(9);
        byteBuffer.putChar('X');
        byteBuffer.putShort((short)4);

        // 取出
        byteBuffer.flip();

        System.out.println(byteBuffer.getInt());
        System.out.println(byteBuffer.getLong());
        System.out.println(byteBuffer.getChar());
        System.out.println(byteBuffer.getShort());

    }
}
