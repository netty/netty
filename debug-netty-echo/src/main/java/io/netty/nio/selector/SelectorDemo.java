package io.netty.nio.selector;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * Selector 选择器
 *
 * @author lxcecho 909231497@qq.com
 * @since 23:21 28-10-2022
 */
public class SelectorDemo {

    private static final int port = 8090;

    private final Selector selector;

    public SelectorDemo() throws Exception {
        this.selector = getSelector();
    }

    private Selector getSelector() throws Exception {
        // create Selector Object
        Selector selector = Selector.open();

        // 创建可选择通道，并配置为非阻塞模式
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);

        // 绑定通道到指定端口
        ServerSocket socket = serverSocketChannel.socket();
        socket.bind(new InetSocketAddress(port));

        // 向 Selector 注册感兴趣的事件
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        return selector;
    }

    /**
     * 开始监听
     */
    public void listen() {
        System.out.println("listen on " + port);
        try {
            while (true) {
                // 该调用会阻塞，知道至少有一个事件发生
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> selectionKeyIterator = selectionKeys.iterator();
                while (selectionKeyIterator.hasNext()) {
                    SelectionKey key = selectionKeyIterator.next();
                    selectionKeyIterator.remove();
                    process(key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据不同的事件做处理
     *
     * @param key
     */
    public void process(SelectionKey key) throws Exception {
        // 接收请求
        if (key.isAcceptable()) {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            String content;
            // 读数据
            SocketChannel socketChannel = (SocketChannel) key.channel();
            int len = socketChannel.read(buffer);
            if (len > 0) {
                buffer.flip();
                content = new String(buffer.array(), 0, len);
                SelectionKey sKey = socketChannel.register(selector, SelectionKey.OP_WRITE);
                sKey.attach(content);
            } else {
                socketChannel.close();
            }
            buffer.clear();
        } else if (key.isWritable()) {
            // 写事件
            SocketChannel socketChannel = (SocketChannel) key.channel();
            String content = (String) key.attachment();
            ByteBuffer buffer = ByteBuffer.wrap(("Output content: " + content).getBytes(StandardCharsets.UTF_8));
            if (buffer != null) {
                socketChannel.write(buffer);
            } else {
                socketChannel.close();
            }
        }


    }

}
