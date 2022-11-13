package io.netty.nio.buffer;

import java.nio.ByteBuffer;

/**
 * 缓冲区分片：Slice Buffer 与原有的 Buffer 共享相同的底层数组
 *
 * @author lxcecho 909231497@qq.com
 * @since 22:29 28-10-2022
 */
public class BufferSlice {

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        // 缓存区中数据为 0~9
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put((byte) i);
        }

        // 创建子缓冲区
        buffer.position(3);
        buffer.limit(7);
        // 3 4 5 6
        ByteBuffer slice = buffer.slice();

        // 改变子缓冲区的内容
        for (int i = 0; i < slice.capacity(); i++) {
            byte b = slice.get(i);
            b *= 10;
            slice.put(i, b);
        }

        buffer.position(0);
        buffer.limit(buffer.capacity());

        while (buffer.remaining() > 0) {
            System.out.println(buffer.get());
        }
    }

}
