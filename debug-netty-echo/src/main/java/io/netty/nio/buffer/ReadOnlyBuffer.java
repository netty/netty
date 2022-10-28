package io.netty.nio.buffer;

import java.nio.ByteBuffer;

/**
 * 只读缓冲区：只可以把常规缓冲区转换为只读缓冲区，而不能将只读缓冲区转换为可写的缓冲区——不可逆
 *
 * @author lxcecho 909231497@qq.com
 * @since 22:45 28-10-2022
 */
public class ReadOnlyBuffer {

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);

        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        // create read only buffer
        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();

        // change the source buffer
        for (int i = 0; i < buffer.capacity(); i++) {
            byte b = buffer.get(i);
            b *= 10;
            buffer.put(i, b);
        }

        readOnlyBuffer.position(0);
        readOnlyBuffer.limit(buffer.capacity());

        // 只读缓冲区的内容也随之改变
        while (readOnlyBuffer.remaining() > 0) {
            System.out.println(readOnlyBuffer.get());
        }

    }

}
