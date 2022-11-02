package io.netty.bio;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author lxcecho 909231497@qq.com
 * @since 2021/2/19
 */
public class BIOServerDemo {

    public static void main(String[] args) throws Exception {
        // 线程池机制
        // 1 创建一个线程池
        // 2 如果有客户端连接，就创建一个线程与之通讯（单独写一个方法）

        ExecutorService executorService = Executors.newCachedThreadPool();

        // 创建 ServerSocket
        ServerSocket serverSocket = new ServerSocket(6666);
        System.out.println("服务器启动了...");
        while (true) {
            System.out.println("线程信息 id=" + Thread.currentThread().getId() + "，名字 name=" + Thread.currentThread().getName());
            // 监听，等待客户端链接
            System.out.println("等待连接....");// 连接成功之后 阻塞在这里
            final Socket socket = serverSocket.accept();
            System.out.println("连接到一个客户端...");

            // 就创建一个线程，与之通讯（单独写一个方法）
            executorService.execute(new Runnable() {
                @Override
                public void run() {// 重写
                    // 可以和客户端通讯
                    handler(socket);
                }
            });
        }
    }

    /**
     * 编写一个handler 方法，和客户端通讯
     *
     * @param socket
     */
    private static void handler(Socket socket) {
        try {
            System.out.println("线程信息 id=" + Thread.currentThread().getId() + "，名字 name=" + Thread.currentThread().getName());
            byte[] bytes = new byte[1024];
            // 通过 socket 获取数据流
            InputStream inputStream = socket.getInputStream();

            // 循环的读取客户端发送的数据
            while (true) {
                System.out.println("线程信息 id=" + Thread.currentThread().getId() + "，名字 name=" + Thread.currentThread().getName());
                System.out.println("read...");// 完成通讯之后，阻塞在这里
                int read = inputStream.read(bytes);
                if (read != -1) {
                    // 输出到客户端发送的数据
                    System.out.println(new String(bytes, 0, read));
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("关闭和client的连接");
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
