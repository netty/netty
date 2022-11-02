package io.netty.nio.channel;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/20
 *
 * 使用 ByteBuffer(缓冲) 和 FileChannel(通道)， 将 "hello,Netty" 写入到file01.txt 中。
 */
public class ChannelDemo {
    public static void main(String[] args){
        String str = "Hello,Netty";
        FileOutputStream fileOutputStream = null;

        try {
            // 创建一个输出流-->Channel
            fileOutputStream = new FileOutputStream("D:\\file01.txt");

            // 通过 FileOutPutStream 获取对应的 FileChannel
            // 这个 FileChannel 真实类型是 FileChannelImpl
            FileChannel fileChannel = fileOutputStream.getChannel();

            // 创建一个缓冲区 ByteBuffer
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

            // 将 str 放入 ByteBuffer
            byteBuffer.put(str.getBytes());

            // 对 ByteBuffer 进行 flip
            byteBuffer.flip();

            // 将 ByteBuffer 数据写入到 FileChannel
            fileChannel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(fileOutputStream != null){
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
