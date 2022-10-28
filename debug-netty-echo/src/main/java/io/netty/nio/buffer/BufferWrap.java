package io.netty.nio.buffer;

import java.nio.ByteBuffer;

/**
 * 手动分配缓冲区
 *
 * @author lxcecho 909231497@qq.com
 * @since 22:27 28-10-2022
 */
public class BufferWrap {
    public void myMethod() {
        // 分配指定大小的缓冲区
        ByteBuffer buffer01 = ByteBuffer.allocate(10);

        // 包装一个现有的数组
        byte[] bytes = new byte[10];
        ByteBuffer buffer02 = ByteBuffer.wrap(bytes);
    }
}
