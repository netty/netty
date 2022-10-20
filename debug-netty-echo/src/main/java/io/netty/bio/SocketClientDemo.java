package io.netty.bio;

import java.io.*;
import java.net.Socket;

/**
 * @author lxcecho 909231497@qq.com
 * @since 23:30 20-10-2022
 */
public class SocketClientDemo {

    public static void main(String[] args) {

        try {
            Socket socket = new Socket("localhost", 8080);
            Thread.sleep(100000);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            bufferedWriter.write("我是客户端，发送了一个消息\n");
            bufferedWriter.flush();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));//输入流
            String serverLine = bufferedReader.readLine(); //读取服务端返回的数据（被阻塞了）
            System.out.println("服务端返回的数据:" + serverLine);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
