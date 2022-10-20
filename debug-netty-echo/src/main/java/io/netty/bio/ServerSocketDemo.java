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
            serverSocket = new ServerSocket(8080);
            while (true) {
                Socket socket = serverSocket.accept(); // 监听客户端连接(连接阻塞）
                System.out.println(socket.getPort());
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
