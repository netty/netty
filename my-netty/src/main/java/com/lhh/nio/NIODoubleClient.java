/**
 * Copyright (C), 2019-2019
 * FileName: NIODoubleClient
 * Author:   s·D·bs
 * Date:     2019/4/23 0:34
 * Description: Double
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.nio;

public class NIODoubleClient {

    private static String DEFAULT_HOST = "127.0.0.1";
    private static Integer DEFAULT_PORT = 8888;
    private static NIODoubleClientHandler clientHandle;

    public static void start() {
        start(DEFAULT_HOST, DEFAULT_PORT);
    }

    private static synchronized void start(String defaultHost, Integer defaultPort) {
        if (clientHandle != null) {
            clientHandle.stop();
        }
        clientHandle = new NIODoubleClientHandler(defaultHost, defaultPort);
        new Thread(clientHandle, "Server").start();
    }

    // 向服务器发送消息
    public static boolean sendMsg(String msg) throws Exception {
        if (msg.equals("q"))
            return false;
        clientHandle.sendMsg(msg);
        return true;
    }

}
