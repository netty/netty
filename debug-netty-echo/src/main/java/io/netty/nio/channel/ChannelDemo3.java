package io.netty.nio.channel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 * <p>
 * 使用 FileChannel(通道) 和 方法  read , write，完成文件的拷贝。
 */
public class ChannelDemo3 {
    public static void main(String[] args) {
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            fileInputStream = new FileInputStream("D:\\file01.txt");
            FileChannel fileChannel01 = fileInputStream.getChannel();

            fileOutputStream = new FileOutputStream("2.txt");
            FileChannel fileChannel02 = fileOutputStream.getChannel();

            ByteBuffer byteBuffer = ByteBuffer.allocate(512);

            while (true) {// 循环读取
                // 重要的操作
                /*public final Buffer clear() {
                    position = 0;
                    limit = capacity;
                    mark = -1;
                    return this;
                }*/

                byteBuffer.clear();// 清空 buffer，重置/复位
                // 把数据从 fileChannel01 读到 buffer 缓冲区
                int read = fileChannel01.read(byteBuffer);
                System.out.println("read = " + read);
                if (read == -1) {// 表示读完
                    break;
                }

                // 将 buffer 中的数据写入到 fileChannel02
                byteBuffer.flip();// 反转此缓冲区（我理解的是 读写模式切换）
                fileChannel02.write(byteBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭相关的流
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
