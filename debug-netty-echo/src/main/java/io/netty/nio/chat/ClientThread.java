package io.netty.nio.chat;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 17.09.2021
 */
public class ClientThread implements Runnable {

    private Selector selector;

    public ClientThread(Selector selector) {
        this.selector = selector;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // 获取 channel 数量
                int readLength = selector.select();

                if (readLength == 0) {
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

                    // 如果可读状态
                    if (selectionKey.isReadable()) {
                        readOperator(selector, selectionKey);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理可读状态操作
     *
     * @param selector
     * @param selectionKey
     */
    private void readOperator(Selector selector, SelectionKey selectionKey) throws Exception {
        // 1 从 SelectionKey 获取到已经就绪的通道
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
            // 广播给其他客户端
            System.out.println(message);
        }

    }

}
