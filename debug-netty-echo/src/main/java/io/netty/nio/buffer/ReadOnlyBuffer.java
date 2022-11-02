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

    private static void m() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(64);

        for (int i = 0; i < 64; i++) {
            byteBuffer.put((byte) i);
        }

        // 读取
        byteBuffer.flip();

        // 得到一个只读 Buffer
        ByteBuffer readOnlyBuffer = byteBuffer.asReadOnlyBuffer();
        System.out.println(readOnlyBuffer.getClass());

        // 读取
        while (readOnlyBuffer.hasRemaining()){
            System.out.print(readOnlyBuffer.get()+" ");
        }
        System.out.println();

        readOnlyBuffer.put((byte) 100);// 抛出 ReadOnlyBufferException 异常
    }

}
