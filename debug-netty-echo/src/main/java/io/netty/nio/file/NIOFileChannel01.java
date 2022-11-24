package io.netty.nio.file;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:15 24-11-2022
 */
public class NIOFileChannel01 {

    public static void main(String[] args) throws Exception{

        String str = "hello,尚硅谷";
        // 创建一个输出流 -> channel
        FileOutputStream fileOutputStream = new FileOutputStream("d:\\file01.txt");

        // 通过 fileOutputStream 获取 对应的 FileChannel
        // 这个 fileChannel 真实 类型是  FileChannelImpl
        FileChannel fileChannel = fileOutputStream.getChannel();

        // 创建一个缓冲区 ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

        // 将 str 放入 byteBuffer
        byteBuffer.put(str.getBytes());


        // 对 byteBuffer 进行 flip
        byteBuffer.flip();

        // 将 byteBuffer 数据写入到 fileChannel
        fileChannel.write(byteBuffer);
        fileOutputStream.close();

    }

}
