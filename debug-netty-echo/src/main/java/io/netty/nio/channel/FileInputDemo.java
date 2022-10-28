package io.netty.nio.channel;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 使用 NIO 读取数据
 * 1. 从 FileInputStream 获取 Channel
 * 2. 创建 Buffer
 * 3. 将数据从 Channel 读到 Buffer 中
 *
 * @author lxcecho 909231497@qq.com
 * @since 0:01 29-10-2022
 */
public class FileInputDemo {

    public static void main(String[] args) throws Exception {
        FileInputStream fis = new FileInputStream("E:\\lxcecho.txt");
        FileChannel fci = fis.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 将数据从 Channel 写入 Buffer
        int read = fci.read(buffer);

        buffer.flip();

        while (buffer.remaining() > 0) {
            byte b = buffer.get();
            System.out.print((char) b);
        }
        fis.close();
    }

}
