package io.netty.nio.channel;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 使用 NIO 从文件中写入数据
 * 1. 从 FileOutputStream 获取 Channel
 * 2. 创建 Buffer，并初始化
 * 3. 将数据从 Buffer 写入到 Channel 中
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:52 28-10-2022
 */
public class FileOutputDemo {

    private static final byte[] messages = {83, 111, 109, 101, 32, 98, 121, 116, 101, 115, 46};

    public static void main(String[] args) throws Exception {
        FileOutputStream fos = new FileOutputStream("E:\\lxcecho.txt");
        FileChannel fco = fos.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        for (int i = 0; i < messages.length; i++) {
            buffer.put(messages[i]);
        }

        buffer.flip();

        // 从 Buffer 中写入 Channel
        fco.write(buffer);

        fos.close();
    }

}
