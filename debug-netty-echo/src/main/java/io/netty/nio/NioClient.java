package io.netty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:30 20-10-2022
 */
public class NioClient {

    static Selector selector;

    public static void main(String[] args) {
        try {
            // 得到一个网络通道
            selector = Selector.open();
            SocketChannel socketChannel = SocketChannel.open();
            // 设置非阻塞
            socketChannel.configureBlocking(false);
            // 提供服务器端的 ip 和 端口，连接服务器
            socketChannel.connect(new InetSocketAddress(8090));
            // 将客户端连接注册到复用器上
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isConnectable()) {
                        // 客户端连接事件处理
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        // 读取服务端消息处理
                        handleRead(key);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        socketChannel.read(byteBuffer);
        System.out.println("Client receive: " + new String(byteBuffer.array(), StandardCharsets.UTF_8));
    }

    private static void handleConnect(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (socketChannel.isConnectionPending()) {
            socketChannel.finishConnect();
        }
        socketChannel.configureBlocking(false);
        socketChannel.write(ByteBuffer.wrap("Hello Server,I'm NIO_Client".getBytes()));
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

}
