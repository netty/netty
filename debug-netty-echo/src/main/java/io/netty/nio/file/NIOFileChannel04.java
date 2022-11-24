package io.netty.nio.file;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:15 24-11-2022
 */
public class NIOFileChannel04 {
    public static void main(String[] args) throws Exception {

        // 创建相关流
        FileInputStream fileInputStream = new FileInputStream("d:\\a.jpg");
        FileOutputStream fileOutputStream = new FileOutputStream("d:\\a2.jpg");

        // 获取各个流对应的 filechannel
        FileChannel sourceCh = fileInputStream.getChannel();
        FileChannel destCh = fileOutputStream.getChannel();

        // 使用 transferForm 完成拷贝
        destCh.transferFrom(sourceCh, 0, sourceCh.size());
        // 关闭相关通道和流
        sourceCh.close();
        destCh.close();
        fileInputStream.close();
        fileOutputStream.close();
    }
}
