package io.netty.nio.selector;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/21
 * <p>
 * 实现服务器端和客户端之间的数据简单通讯（非阻塞）
 */
public class NIOServer {
    public static void main(String[] args) throws Exception {
        // 创建 ServerSocketChannel --> ServerSocket
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        // 得到一个 Selector 对象
        Selector selector = Selector.open();

        // 绑定一个端口 6666，在服务器监听
        InetSocketAddress inetSocketAddress = new InetSocketAddress(6666);
        serverSocketChannel.socket().bind(inetSocketAddress);

        // 设置为 非阻塞
        serverSocketChannel.configureBlocking(false);

        // 把 serverSocketChannel 注册到 selector 关心事件为 OP_ACCEPT
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 循环等待客户端连接
        while (true) {
            // 这里等待 1秒 ，如果没有事件发生，返回
            if (selector.select(1000) == 0) {// 没有时间发生
                System.out.println("服务器等待了1秒，无连接");
                continue;
            }
            // 如果返回的 >0 ，就获取到相关的 selectionKey 集合
            // 1. 如果返回的 >0，表示已经获取到关注的事件
            // selection.selectKeys() 返回关注事件的集合
            // 通过 selectKeys 反向获取通道
            Set<SelectionKey> selectionKeys = selector.selectedKeys();

            // 遍历 Set<SelectionKey>，使用 迭代器遍历
            Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
            while (keyIterator.hasNext()) {
                // 获取到 SelectionKey
                SelectionKey selectionKey = keyIterator.next();
                // 根据 selectionKey 对应的通道发生的事件做相应的处理
                if (selectionKey.isAcceptable()) {// 如果是 OP_ACCEPT，有新的客户端连接
                    // 该客户端生成一个 SocketChannel
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    System.out.println("客户端连接成功，生成了一个 socketChannel " + socketChannel.hashCode());
                    // 将 SocketChannel 设置为非阻塞
                    socketChannel.configureBlocking(false);
                    // 将 socketChannel 注册到 Selector，关注事件为 OP_READ，同时给 socketChannel
                    // 关联一个 Buffer
                    socketChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024));
                }
                if (selectionKey.isReadable()) {// 发生 OP_READ
                    // 通过 selectionKey 反向获取对应的 Channel
                    SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                    // 获取到该 Channel 关联的 buffer
                    ByteBuffer byteBuffer = (ByteBuffer) selectionKey.attachment();
                    socketChannel.read(byteBuffer);
                    System.out.println("from 客户端 " + new String(byteBuffer.array()));
                }
                // 手动从集合中移动当前的 selectionKey，防止重复操作
                keyIterator.remove();
            }
        }
    }
}
