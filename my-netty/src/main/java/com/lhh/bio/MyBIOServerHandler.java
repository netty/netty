package com.lhh.bio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyBIOServerHandler {
    private static final int PORT = 8888; // 服务器对外的端口号  目前和客户端保持一致

    public static void main(String[] args) throws IOException {
        ServerSocket server = null;
        Socket socket = null;
        ThreadPoolExecutor executor = null;
        try {
            server = new ServerSocket(PORT); // ServerSocket 启动监听端口
            System.out.println("BIO Server 服务器启动.........");
            /*--------------传统的新增线程处理----------------*/
            /*while (true) {
                // 服务器监听：阻塞，等待Client请求
                socket = server.accept();
                System.out.println("server 服务器确认请求 : " + socket);
                // 服务器连接确认：确认Client请求后，创建线程执行任务  。很明显的问题，若每接收一次请求就要创建一个线程，显然是不合理的。
                new Thread(new ITDragonBIOServerHandler(socket)).start();
            } */
            /*--------------通过线程池处理缓解高并发给程序带来的压力（伪异步IO编程）----------------*/
            //自定义线程池ThreadPoolExecutor 其实可以实现ExecutorService 实现一些相关的日志控制
            executor = new ThreadPoolExecutor(10, 100, 1000, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(50));
            while (true) {
                socket = server.accept();  // 服务器监听：阻塞，等待Client请求
                MyBIOServer serverHandler = new MyBIOServer(socket);
                executor.execute(serverHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != socket) {
                    socket.close();
                }
                if (null != server) {
                    server.close();
                    System.out.println("BIO Server 服务器关闭了！！！！");
                }
                executor.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

