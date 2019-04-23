/**
 * Copyright (C), 2019-2019
 * FileName: NIODoubleServerHandler
 * Author:   s·D·bs
 * Date:     2019/4/23 0:36
 * Description: DoubleServerHandler处理器
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.nio;

import com.lhh.utils.CalculatorUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

@SuppressWarnings(value = {"all"})
public class NIODoubleServerHandler implements Runnable {

    private Selector selector;                // 多路复用选择器
    private ServerSocketChannel serverChannel;  // 服务器通道
    private volatile boolean started;            // 服务器状态，避免服务器关闭后还继续循环
    private final int BUFFER_SIZE = 1024;        // 缓冲区大小

    public NIODoubleServerHandler(Integer port) {
        try {
            // 创建多路复用器
            selector = Selector.open();
            // 打开监听通道
            serverChannel = ServerSocketChannel.open();
            // 设置服务器通道为非阻塞模式，true为阻塞，false为非阻塞
            serverChannel.configureBlocking(false);// 开启非阻塞模式
            // 绑定端口
            serverChannel.socket().bind(new InetSocketAddress(port));
            // 通道注册到多路复用器上，并监听阻塞事件
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            // 标记服务器已开启
            started = true;
            System.out.println("服务器已启动 >>>>>>>> 端口号：" + port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1); // 非正常退出程序
        }
    }

    public void stop() {
        started = false;
    }


    /**
     * selector.select() 				阻塞到至少有一个通道在你注册的事件上就绪
     * selector.select(long timeOut) 	阻塞到至少有一个通道在你注册的事件上就绪或者超时timeOut
     * selector.selectNow() 			立即返回。如果没有就绪的通道则返回0
     * select方法的返回值表示就绪通道的个数。
     */
    @Override
    public void run() {
        while (started) {        // 循环遍历selector
            try {
                // 多路复用器监听阻塞
                selector.select();
                // 多路复用器已经选择的结果集
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                SelectionKey key = null;
                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();
                    handleInput(key);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        if (null != selector) {    // 释放资源
            try {
                selector.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws IOException{
        if (!key.isValid()) {
            return;
        }
        // 处理新接入的请求消息
        if (key.isAcceptable()) {
            // 获取通道服务
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            // 通过ServerSocketChannel的accept创建SocketChannel实例
            SocketChannel socketChannel = serverSocketChannel.accept();
            // 设置服务器通道为非阻塞模式，true为阻塞，false为非阻塞
            socketChannel.configureBlocking(false);
            // 把通道注册到多路复用器上，并设置读取标识
            socketChannel.register(selector, SelectionKey.OP_READ);
        }
        if (key.isReadable()) {
            SocketChannel sc = (SocketChannel) key.channel();
            // 创建ByteBuffer，并开辟一个1M的缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            // 读取请求码流，返回读取到的字节数
            int readBytes = sc.read(buffer);
            // 读取到字节，对字节进行编解码
            if (0 < readBytes) {
                // 将缓冲区数据复位
                buffer.flip();
                // 根据缓冲区可读字节数创建字节数组
                byte[] bytes = new byte[buffer.remaining()];
                // 将缓冲区可读字节数组复制到新建的数组中
                buffer.get(bytes);
                String expression = new String(bytes, "UTF-8");
                System.out.println("服务器收到消息：" + expression);
                // 处理数据
                String result = CalculatorUtil.cal(expression).toString();
                // 返回数据消息
                doWrite(sc, result);
            } else if (0 > readBytes) {
                key.cancel();
                sc.close();
            }
        }

    }

    // 异步发送应答消息
    private void doWrite(SocketChannel channel, String response) throws IOException {
        // 将消息编码为字节数组
        byte[] bytes = response.getBytes();
        // 根据数组容量创建ByteBuffer
        ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
        // 将字节数组复制到缓冲区
        writeBuffer.put(bytes);
        // 将缓冲区数据复位
        writeBuffer.flip();
        // 发送缓冲区的字节数组
        channel.write(writeBuffer);
    }


}
