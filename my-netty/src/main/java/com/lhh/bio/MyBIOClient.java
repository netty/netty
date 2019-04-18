package com.lhh.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class MyBIOClient {
    private static int PORT = 8888;
    private static String IP_ADDRESS = "127.0.0.1";

    public static void main(String[] args) {
        //十个客户端
        for (int i = 0; i < 10; i++) {
            clientReq(i);
        }
    }

    private static void clientReq(int i) {
        Socket socket = null;
        BufferedReader reader = null;
        PrintWriter writer = null;
        try {
            socket = new Socket(IP_ADDRESS, PORT); // Socket 发起连接操作。连接成功后，双方通过输入和输出流进行同步阻塞式通信
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 获取返回内容
            writer = new PrintWriter(socket.getOutputStream(), true);
            String[] operators = {"+", "-", "*", "/"};
            Random random = new Random(System.currentTimeMillis());
            String expression = random.nextInt(10) + operators[random.nextInt(4)] + (random.nextInt(10) + 1);
            writer.println(expression); // 向服务器端发送数据
            System.out.println(i + " 客户端打印返回数据 : " + reader.readLine());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != reader) {
                    reader.close();
                }
                if (null != socket) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
