/**
 * Copyright (C), 2019-2019
 * FileName: AIOServer
 * Author:   s·D·js
 * Date:     2019/4/22 22:16
 * Description: AIO服务端简单实现
 * Motto: 0.45%
 *
 * @create 2019/4/22
 * @since 1.0.0
 */


package com.lhh.aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIOServer {
    private ExecutorService executorService;        // 线程池
    private AsynchronousChannelGroup threadGroup;    // 通道组
    public AsynchronousServerSocketChannel asynServerSocketChannel;  // 服务器通道

    public void start(Integer port) {
        try {
            //创建线程池
            executorService = Executors.newCachedThreadPool();
            //创建线程通道
            threadGroup=AsynchronousChannelGroup.withCachedThreadPool(executorService,1);
            //创建服务器通道
            asynServerSocketChannel = AsynchronousServerSocketChannel.open(threadGroup);
            //进行绑定
            asynServerSocketChannel.bind(new InetSocketAddress(port));
            System.out.println("server start , port : " + port);
            //等待客户端请求
            asynServerSocketChannel.accept(this, new AIOServerHandler());
            // 一直阻塞 不让服务器停止，真实环境是在tomcat下运行，所以不需要这行代码
            Thread.sleep(Integer.MAX_VALUE);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        AIOServer aioServer = new AIOServer();
        aioServer.start(9999);
    }
}