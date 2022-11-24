package io.netty.nio.file;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:15 24-11-2022
 */
public class NIOFileChannel02 {
    public static void main(String[] args) throws Exception {

        // 创建文件的输入流
        File file = new File("d:\\file01.txt");
        FileInputStream fileInputStream = new FileInputStream(file);

        // 通过 fileInputStream 获取对应的 FileChannel -> 实际类型  FileChannelImpl
        FileChannel fileChannel = fileInputStream.getChannel();

        // 创建缓冲区
        ByteBuffer byteBuffer = ByteBuffer.allocate((int) file.length());

        // 将通道的数据读入到 Buffer
        fileChannel.read(byteBuffer);

        // 将 byteBuffer 的 字节数据 转成 String
        System.out.println(new String(byteBuffer.array()));
        fileInputStream.close();

    }
}
