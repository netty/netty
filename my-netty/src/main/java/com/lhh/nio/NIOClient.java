/**
 * Copyright (C), 2019-2019
 * FileName: NIOClient
 * Author:   s·D·bs
 * Date:     2019/4/23 0:26
 * Description: NIO客户端的手写实现
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NIOClient {

    private final static int PORT = 8888;
    private final static int BUFFER_SIZE = 1024;
    private final static String IP_ADDRESS = "127.0.0.1";

    //缓冲区 缓冲通道
    public static void main(String[] args) {
        clientReq();
    }

    private static void clientReq() {
        // 1.创建连接地址
        InetSocketAddress inetSocketAddress = new InetSocketAddress(IP_ADDRESS, PORT);
        // 2.声明一个连接通道
        SocketChannel socketChannel = null;
        // 3.创建一个缓冲区
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        try {
            // 4.打开通道
            socketChannel = SocketChannel.open();
            // 5.连接服务器
            socketChannel.connect(inetSocketAddress);
            while (true) {
                // 6.定义一个字节数组，然后使用系统录入功能：
                byte[] bytes = new byte[BUFFER_SIZE];
                // 7.键盘输入数据
                System.in.read(bytes);
                // 8.把数据放到缓冲区中
                byteBuffer.put(bytes);
                // 9.对缓冲区进行复位
                byteBuffer.flip();
                // 10.写出数据
                socketChannel.write(byteBuffer);
                // 11.清空缓冲区数据
                byteBuffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (null != socketChannel) {
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
