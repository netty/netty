package io.netty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:08 20-10-2022
 */
public class NioServer {

    static Selector selector;

    public static void main(String[] args) {
        try {
            // selector 必须是非阻塞
            selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            // 设置为非阻塞
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(8090));
            // 把连接事件注册到多路复用器上
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select(); // 阻塞机制
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey next = iterator.next();
                    // 把对应事件移除掉，避免重复处理
                    iterator.remove();
                    if (next.isAcceptable()) {
                        // 连接事件
                        handleAccept(next);
                    } else if (next.isReadable()) {
                        // 读的就绪事件
                        handleRead(next);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 处理连接服务端的连接
     *
     * @param key
     */
    private static void handleRead(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            // 一定会有一个连接
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.write(ByteBuffer.wrap("Hello, Client, I'm NIO_Server".getBytes(StandardCharsets.UTF_8)));
            // 注册读事件
            socketChannel.register(selector, SelectionKey.OP_READ);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取客户端发来的消息
     *
     * @param key
     */
    private static void handleAccept(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            // 将通道的数据读到缓冲区
            socketChannel.read(byteBuffer); // 这里一定有值
            System.out.println("Server recive msg: " + new String(byteBuffer.array()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
