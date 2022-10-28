package io.netty.nio.buffer;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 直接缓冲区
 *
 * @author lxcecho 909231497@qq.com
 * @since 22:57 28-10-2022
 */
public class DirectBuffer {
    public static void main(String[] args) throws Exception {

        // 首先从磁盘上读取之前写出的文件内容
        FileInputStream fis = new FileInputStream("E:\\lxcecho.txt");
        FileChannel fci = fis.getChannel();

        // 把读取的内容写入一个新文件
        String outfile = String.format("E:\\lxcecho_copy.txt");
        FileOutputStream fos = new FileOutputStream(outfile);
        FileChannel fco = fos.getChannel();

        // 分配直接内存
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        while (true) {
            buffer.clear();
            // 从通道中读到缓冲区
            int read = fci.read(buffer);

            if (read == -1) {
                break;
            }

            buffer.flip();

            // 从缓冲区中读到通道
            fco.write(buffer);
        }
    }
}
