package io.netty.nio.channel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 *
 * 使用 ByteBuffer(缓冲) 和 FileChannel(通道)， 将 file01.txt 中的数据读入到程序，并显示在控制台屏幕。
 */
public class ChannelDemo2 {
    public static void main(String[] args) {
        FileInputStream fileInputStream =null;

        try {
            // 创建文件的输入流
            File file = new File("D:\\file01.txt");
            fileInputStream = new FileInputStream(file);

            // 通过 FileInputStream 获取对应的 FileChannel --> 实际类型 FileChannelImpl
            FileChannel fileChannel = fileInputStream.getChannel();

            // 创建缓冲区
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) file.length());

            // 将通道的数据读入到 Buffer
            fileChannel.read(byteBuffer);

            // 将 ByteBuffer 的字节数据转成 String
            System.out.println(new String(byteBuffer.array()));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fileInputStream != null){
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
