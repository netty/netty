/**
 * Copyright (C), 2019-2019
 * FileName: AIOClient
 * Author:   s·D·js
 * Date:     2019/4/22 22:15
 * Description: AIO异步非堵塞客户端简单实现
 * Motto: 0.45%
 *
 * @create 2019/4/22
 * @since 1.0.0
 */


package com.lhh.aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AIOClient implements Runnable {

    private static Integer PORT = 9999;
    private static String IP_ADDRESS = "127.0.0.1";
    private AsynchronousSocketChannel asynSocketChannel;

    public AIOClient() {
        try {
            asynSocketChannel = AsynchronousSocketChannel.open();  // 打开通道
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connect() {
        //创建连接
        asynSocketChannel.connect(new InetSocketAddress(IP_ADDRESS, PORT));
    }

    /**
     * 功能描述: <br>
     * 〈异步请求不会管你有没有连接成功，都会继续执行〉<br>
     * java.nio.channels.NotYetConnectedException
     */
    public void write(String request)  {
        try {
            asynSocketChannel.write(ByteBuffer.wrap(request.getBytes())).get();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            asynSocketChannel.read(byteBuffer).get();
            byteBuffer.flip();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);//把缓冲区数据放到byte数组
            System.out.println(new String(bytes,"utf-8").trim());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            AIOClient myClient = new AIOClient();
            myClient.connect();
            new Thread(myClient, "myClient"+i).start();
            String []operators = {"+","-","*","/"};
            Random random = new Random(System.currentTimeMillis());
            String expression = random.nextInt(10)+operators[random.nextInt(4)]+(random.nextInt(10)+1);
            myClient.write(expression);
        }
    }

    @Override
    public void run() {
//        while (true){
//
//        }

        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //前面的内容一下就出现说明是非堵塞
        System.out.println("AIOClient run!");
    }
}
