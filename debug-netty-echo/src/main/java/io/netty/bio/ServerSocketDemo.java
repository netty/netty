package io.netty.bio;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:30 20-10-2022
 */
public class ServerSocketDemo {

    static ExecutorService executorService = Executors.newFixedThreadPool(20);

    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        try {
            // localhost: 8080
            /**
             * TCP 的服务端要先监听一个端口，一般是先调用 bind 函数，给这个 Socket 赋予一个 IP 地址和端口。
             * 为什么需要端口呢？要知道，你写的是一个应用程序，当一个网络包来的时候，内核要通过 TCP 头里面的这个端口，
             * 来找到你这个应用程序，把包给你。为什么要 IP 地址呢？有时候，一台机器会有多个网卡，也就会有多个 IP 地址，
             * 你可以选择监听所有的网卡，也可以选择监听一个网卡，这样，只有发给这个网卡的包，才会给你。
             */
            serverSocket = new ServerSocket(8080);
            while (true) {
                // 阻塞等待客户端连接，监听客户端连接(连接阻塞），有客户请求到来则产生一个 Socket 对象
                // 接下来，服务端调用 accept 函数，拿出一个已经完成的连接进行处理。如果还没有完成，就要等着。
                Socket socket = serverSocket.accept();
                System.out.println(socket.getPort());
                // 连接建立成功之后，双方开始通过 read 和 write 函数来读写数据，就像往一个文件流里面写东西一样。放在线程池中执行
                executorService.execute(new SocketThread(socket)); // 异步
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // TODO
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
