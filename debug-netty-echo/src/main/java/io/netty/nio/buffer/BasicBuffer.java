package io.netty.nio.buffer;

import java.nio.IntBuffer;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/19
 *
 * Buffer 的使用
 */
public class BasicBuffer {
    public static void main(String[] args) {
        // 创建一个 Buffer，大小为5，既可以存放5个 int
        IntBuffer intBuffer = IntBuffer.allocate(5);

        // 向 buffer 存放数据
        for (int i = 0; i < intBuffer.capacity(); i++) {
            intBuffer.put(i*2);
        }

        // 从 buffer 读取数据
        // 将 buffer 转换，读写切换
        intBuffer.flip();

        while (intBuffer.hasRemaining()){
            System.out.println(intBuffer.get());
        }
    }
}
