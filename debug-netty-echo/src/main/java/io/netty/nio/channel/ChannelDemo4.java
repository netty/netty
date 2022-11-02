package io.netty.nio.channel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 * <p>
 * 使用 FileChannel(通道) 和 方法  transferFrom ，完成文件的拷贝。
 */
public class ChannelDemo4 {
    public static void main(String[] args) {
        // 创建相关流
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        FileChannel fileChannel01 = null;
        FileChannel fileChannel02 = null;

        try {
            fileInputStream = new FileInputStream("D:\\4-1-1.jpg");
            fileOutputStream = new FileOutputStream("D:\\4-1-2.jpg");

            // 获取各个流对应的 FileChannel
            fileChannel01 = fileInputStream.getChannel();
            fileChannel02 = fileOutputStream.getChannel();

            // 使用 transferFrom 完成拷贝
//            fileChannel02.transferFrom(fileChannel01, 0, fileChannel01.size());
            // 使用 transferTo
            fileChannel01.transferTo(0,fileChannel01.size(),fileChannel02);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // 关闭相应的通道和流
                if (fileChannel01 != null) {
                    fileChannel01.close();
                }
                if (fileChannel02 != null) {
                    fileChannel02.close();
                }
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
