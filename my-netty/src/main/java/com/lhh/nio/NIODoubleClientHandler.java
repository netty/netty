/**
 * Copyright (C), 2019-2019
 * FileName: NIODoubleClientHandler
 * Author:   s·D·bs
 * Date:     2019/4/23 0:35
 * Description: Double NIOClient处理器
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIODoubleClientHandler implements Runnable {

    private String host;
    private Integer port;
    private Selector selector;
    private SocketChannel socketChannel;
    private volatile boolean started;


    public NIODoubleClientHandler(String ip, Integer port) {
        this.host = ip;
        this.port = port;
        //打开选择器
        try {
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            //完成打开动作标记完成
            started = true;
        } catch (IOException e) {
            e.printStackTrace();
            /**
             * System.exit(0)是将你的整个虚拟机里的内容都停掉了 ，而dispose()只是关闭这个窗口，
             * 但是并没有停止整个application exit() 。无论如何，内存都释放了！也就是说连JVM都关闭了，
             * 内存里根本不可能还有什么东西
             * System.exit(0)是正常退出程序，而System.exit(1)或者说非0表示非正常退出程序
             * 不然机器就卡死
             */
            System.exit(1);
        }

    }

    public void stop() {
        started = false;
    }

    @Override
    public void run() {
        doConnect();
        while (started) {
            try {
                //这里诠释了使用多路复用选择器轮询拿到SelectionKey
                //根据它
                selector.select();

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                SelectionKey key = null;
                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();
                    //操作socketChannel 循环操作
                    handleInput(key);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //循环完毕进行关闭
        if (null != selector) {
            try {
                selector.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws IOException {
        if (key.isValid()) {//有效的时候
            SocketChannel socketChannel = (SocketChannel) key.channel();
            if (key.isConnectable()) {//可连接
                if (!socketChannel.isConnected()) {
                    System.exit(1);
                }
            }
            if (key.isReadable()) {//可读
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                int read = socketChannel.read(buffer);
                if (read > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    String result = new String(bytes, "UTF-8");
                    System.out.println("客户端收到消息：" + result);
                } else if (read < 0) {
                    key.cancel();
                    socketChannel.close();
                }
            }

        }

    }

    private void doConnect() {
        try {
            boolean connect = socketChannel.connect(new InetSocketAddress(host, port));
            if (!connect) {//无法连接直接重新注册socketChannel到selector上
                socketChannel.register(selector, SelectionKey.OP_CONNECT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void sendMsg(String msg) throws Exception {
        socketChannel.register(selector, SelectionKey.OP_READ);
        doWrite(socketChannel, msg);
    }

    //真实中处理的业务逻辑这里进行业务处理
    private void doWrite(SocketChannel socketChannel, String msg) throws Exception {
        byte[] bytes = msg.getBytes();
        ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
        writeBuffer.put(bytes);
        writeBuffer.flip();
        socketChannel.write(writeBuffer);
    }
}
