package io.netty.nio.zerocopy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 */
public class NewIoServer {

    public static void main(String[] args) throws Exception {

        InetSocketAddress address = new InetSocketAddress(7001);
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverSocketChannel.socket();

        serverSocket.bind(address);

        // new buffer
        ByteBuffer byteBuffer = ByteBuffer.allocate(4096);
        while (true) {
            SocketChannel socketChannel = serverSocketChannel.accept();
            int read = 0;
            while (-1 != read) {
                try {
                    read = socketChannel.read(byteBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 倒带 position=0 mark作废
                byteBuffer.rewind();
            }
        }
    }

}
