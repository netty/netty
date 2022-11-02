package io.netty.nio.groupchat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 *
 * 1 服务器启动并监听6667
 * 2 服务器接收客户端信息，并实现转发【处理上线和离线】
 */
public class GroupChatServer {

    // 定义属性

    private Selector selector;

    private ServerSocketChannel listenChannel;

    private static final int PORT = 6667;

    /**
     * 构造器 初始化工作
     */
    public GroupChatServer() {
        try {
            // 得到选择器
            selector = Selector.open();
            // ServerSocketChannel
            listenChannel = ServerSocketChannel.open();
            // 绑定端口
            listenChannel.socket().bind(new InetSocketAddress(PORT));
            // 设置非阻塞模式
            listenChannel.configureBlocking(false);
            // 将该 listenChannel 注册到 Selector
            listenChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听
     */
    public void listen() {
        System.out.println("监听线程：" + Thread.currentThread().getName());
        try {
            while (true) {
                int count = selector.select();
                // 有事件处理
                if (count > 0) {
                    // 遍历得到 selectionKey 集合
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        // 取出 selectionKey
                        SelectionKey key = iterator.next();

                        // 监听 accept 事件
                        if (key.isAcceptable()) {
                            SocketChannel sc = listenChannel.accept();
                            sc.configureBlocking(false);

                            // 将该 sc 注册到 selector
                            sc.register(selector, SelectionKey.OP_READ);

                            // 提示上线
                            System.out.println(sc.getRemoteAddress() + " 上线...");
                        }

                        // 通道发送 read 事件，即通道是可读的
                        if (key.isReadable()) {
                            // 处理读
                            readData(key);
                        }
                        // 当前的 key 删除，防止重复处理
                        iterator.remove();
                    }
                } else {
                    System.out.println("waiting...");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 发生异常处理
        }
    }

    /**
     * 读取客户端消息
     *
     * @param key
     */
    private void readData(SelectionKey key) {
        // 取到关联的 channel
        SocketChannel channel = null;

        try {
            // 得到 channel
            channel = (SocketChannel) key.channel();
            // 创建 buffer
            ByteBuffer buffer = ByteBuffer.allocate(1024);

            int count = channel.read(buffer);
            // 根据 count 的值做处理
            if (count > 0) {
                // 把缓存区的数据转成字符串
                String msg = new String(buffer.array());
                // 输出该消息
                System.out.println("from 客户端 : " + msg);

                // 向其他客户端转发消息（去掉自己），专门写一个方法来处理
                sendInfoToOtherClients(msg, channel);
            }
        } catch (IOException e) {
            try {
                System.out.println(channel.getRemoteAddress() + " 下线 ...");
                // 取消注册
                key.cancel();
                // 关闭通道
                channel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 转发消息给其他客户端（通道）
     *
     * @param msg
     * @param self
     */
    private void sendInfoToOtherClients(String msg, SocketChannel self) throws Exception {
        System.out.println("服务器转发消息中...");
        System.out.println("服务器转发数据给客户端线程：" + Thread.currentThread().getName());

        // 遍历 所有注册到 selector 上的 SocketChannel 并排除 self
        for (SelectionKey key : selector.keys()) {
            // 通过 key 取出对应的 SocketChannel
            Channel targetChannel = key.channel();
            // 排除自己
            if (targetChannel instanceof SocketChannel && targetChannel != self) {
                // 转型
                SocketChannel dest = (SocketChannel) targetChannel;
                // 将 msg 存储到 buffer
                ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
                // 将 buffer 的数据写入通道
                dest.write(buffer);
            }
        }
    }

    public static void main(String[] args) {
        // 创建服务器对象
        GroupChatServer groupChatServer = new GroupChatServer();
        groupChatServer.listen();
    }

}

/**
 * 可以写一个 Handler 处理
 */
class MyHandler {
    public void readData() {

    }

    public void sendInfoToOtherClients() {

    }
}
