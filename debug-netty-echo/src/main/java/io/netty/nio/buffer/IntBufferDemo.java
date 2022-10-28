package io.netty.nio.buffer;

import java.nio.IntBuffer;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:39 27-10-2022
 */
public class IntBufferDemo {
    public static void main(String[] args) {
        /**
         * 分配新的 int 缓冲区，参数为缓冲区容量，新缓冲区的当前位置为 0，其界限（限制位置）为其容量，
         * 它具有一个底层实现数组，其数组偏移量为 0；
         */
        IntBuffer buffer = IntBuffer.allocate(8);
        for (int i = 0; i < buffer.capacity(); i++) {
            int j = 2 * (i + 1);
            // 将给定证书写入此缓冲区的当前位置，当前位置递增
            buffer.put(j);
        }
        // 重设此缓冲区，将限制位置设置为当前位置，然后将当前位置设置为 0
        buffer.flip();
        // 查看在当前位置和限制位置之间是否有元素
        while (buffer.hasRemaining()) {
            // 读取此缓冲区当前位置的整数，然后当前位置递增
            int j = buffer.get();
            System.out.print(j + " ");
        }
    }

}
