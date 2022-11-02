package io.netty.nio.chat;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Scanner;

/**
 * @author lxcecho 909231497@qq.com
 * @since 17.09.2021
 *
 * 客户端
 */
public class ChatClient {

    /**
     * 启动客户端方法
     *
     * @param name
     * @throws Exception
     */
    public void startClient(String name) throws Exception {
        // 连接服务端
        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress("127.0.0.1", 8090));

        // 接收服务端响应数据
        Selector selector = Selector.open();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);

        // 创建线程
        new Thread(new ClientThread(selector)).start();

        // 向服务端发送消息
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String msg = scanner.nextLine();
            if(msg.length() > 0) {
                socketChannel.write(Charset.forName("UTF-8").encode(name +" : " +msg));
            }
        }
    }

    public static void main(String[] args) {

    }

}
