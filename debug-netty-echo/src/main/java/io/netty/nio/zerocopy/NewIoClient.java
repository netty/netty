package io.netty.nio.zerocopy;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 */
public class NewIoClient {

    public static void main(String[] args) throws Exception {

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost",7001));
        String filename = "protoc-3.6.1-win32.zip";

        // 得到一个文件 channel
        FileChannel fileChannel = new FileInputStream(filename).getChannel();

        // 准备发送
        long startTime = System.currentTimeMillis();

        // 在 linux 下一个 transferTo 方法就可以完成传输
        // 在 windows 下，一次第哦啊用 transferTo 只能发送 8M，就需要分段传输文件，而且主要传输时的位置 ---> 思考？？？
        // transferTo 底层使用到 零拷贝
        long transferCount = fileChannel.transferTo(0, fileChannel.size(), socketChannel);

        System.out.println("发送总字节数 " + transferCount + "，耗时 " + (System.currentTimeMillis() - startTime));

        // close
        fileChannel.close();
    }

}
