package io.netty.nio.chat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 17.09.2021
 * <p>
 * 服务端
 */
public class ChatServer {

    /**
     * 服务端启动的方法
     *
     * @throws Exception
     */
    public void startServer() throws Exception {
        // 1 创建 Selector 选择器
        Selector selector = Selector.open();

        // 2 创建 ServerSocketChannel 通道
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

        // 3 为 channel 通道绑定监听端口
        serverSocketChannel.bind(new InetSocketAddress(8090));

        // 设置非阻塞模式
        serverSocketChannel.configureBlocking(false);

        // 4 把 channel 通道注册到 selector 选择器上
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("服务器已经启动成功了...");

        // 5 循环，等待有新链接接入
        // for (;;) {
        while (true) {
            // 获取 channel 数量
            int readChannels = selector.select();

            // 如果为0，阻塞
            if (readChannels == 0) {
                continue;
            }

            // 获取可用的 channel
            Set<SelectionKey> selectionKeys = selector.selectedKeys();

            // 遍历集合
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();

                // 移除 set 集合当前 selectionKey
                iterator.remove();

                // 6 根据就绪状态，调用对应方法实现具体的操作
                // 6.1 如果 accept 状态
                if (selectionKey.isAcceptable()) {
                    acceptOperator(serverSocketChannel, selector);
                }
                // 6.2 如果 read 状态
                if (selectionKey.isReadable()) {
                    readOperator(selector, selectionKey);
                }
            }
        }
    }

    /**
     * 处理可读状态操作
     *
     * @param selector
     * @param selectionKey
     * @throws Exception
     */
    private void readOperator(Selector selector, SelectionKey selectionKey) throws Exception {
        // 1 从 SelectionKey 获取已经就绪的通道
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

        // 2 创建 buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 3 循环读取客户端消息
        int readLength = socketChannel.read(buffer);
        String message = "";
        if (readLength > 0) {
            // 切换读模式
            buffer.flip();
            // 读取内容
            message += Charset.forName("UTF-8").decode(buffer);
        }

        // 4 将 channel 再次注册到选择器上，监听可读状态
        socketChannel.register(selector, SelectionKey.OP_READ);

        // 5 把客户端发送的消息，广播到其他客户端
        if (message.length() > 0) {
            // 广播其他客户端
            System.out.println(message);
            castOtherClient(message, selector, socketChannel);
        }

    }

    /**
     * 广播到其他客户端
     *
     * @param message
     * @param selector
     * @param socketChannel
     * @throws Exception
     */
    private void castOtherClient(String message, Selector selector, SocketChannel socketChannel) throws Exception {
        // 1 获取所有已经接入 channel
        Set<SelectionKey> selectionKeySet = selector.keys();

        // 2 循环获取所有 channel 广播消息
        for (SelectionKey selectionKey : selectionKeySet) {
            // 获取每个 channel
            Channel tarChannel = selectionKey.channel();
            // 不需要给自己发送
            if (tarChannel instanceof SocketChannel && tarChannel != socketChannel) {
                ((SocketChannel) tarChannel).write(Charset.forName("UTF-8").encode(message));
            }
        }
    }

    /**
     * 处理接入状态操作
     *
     * @param serverSocketChannel
     * @param selector
     * @throws Exception
     */
    private void acceptOperator(ServerSocketChannel serverSocketChannel, Selector selector) throws Exception {
        // 1 接入状态，创建 SocketChannel
        SocketChannel socketChannel = serverSocketChannel.accept();

        // 2 把 SocketChannel 设置非阻塞模式
        socketChannel.configureBlocking(false);

        // 3 把 Channel 注册到 selector 选择器上，监听可读状态
        socketChannel.register(selector, SelectionKey.OP_READ);

        // 4 客户端回复信息
        socketChannel.write(Charset.forName("UTF-8").encode("欢迎进入聊天室"));
    }

    public static void main(String[] args) {
        try {
            // 启动主方法
            new ChatServer().startServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
