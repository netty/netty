package io.netty.nio.groupchat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Scanner;

/**
 * @author lxcecho 909231497@qq.com
 * @since 29.05.2021
 * <p>
 * 1 连接服务器
 * 2 发送消息
 * 3 接受服务器消息
 */
public class GroupChatClient {

    private final String HOST = "127.0.0.1";
    private final int PORT = 6667;
    private Selector selector;

    private SocketChannel socketChannel;

    private String username;

    public GroupChatClient() {
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open(new InetSocketAddress(HOST, PORT));
            // 设置非阻塞
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            username = socketChannel.getLocalAddress().toString().substring(1);
            System.out.println(username + " is ok ...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 向服务器发送消息
     *
     * @param info
     */
    public void sendInfo(String info) {
        info = username + " 说 : " + info;
        try {
            socketChannel.write(ByteBuffer.wrap(info.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取从服务器端回复的消息
     */
    public void readInfo() {
        try {
            int readChannels = selector.select();
            // 有可以用的通道
            if (readChannels > 0) {
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isReadable()) {
                        // 得到相关通道
                        SocketChannel sc = (SocketChannel) key.channel();
                        // 得到一个 buffer
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        // 读取
                        sc.read(buffer);
                        // 把读到的缓冲区的数据转成字符串
                        String msg = new String(buffer.array());
                        System.out.println(msg.trim());
                    }
                }
                // 删除当前的 selectionKey ，防止重复操作
                iterator.remove();
            } else {
                // System.out.println("没有可以用的通道。。。");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GroupChatClient groupChatClient = new GroupChatClient();

        new Thread() {
            @Override
            public void run() {
                while (true) {
                    groupChatClient.readInfo();
                    try {
                        sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();

        // 发送数据给服务端
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String s = scanner.nextLine();
            groupChatClient.sendInfo(s);
        }

    }

}
