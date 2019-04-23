/**
 * Copyright (C), 2019-2019
 * FileName: NIODoubleServer
 * Author:   s·D·bs
 * Date:     2019/4/23 0:35
 * Description: DoubleServer的创建
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.nio;

public class NIODoubleServer {

    private static Integer DEFAULT_PORT = 8888;
    private static NIODoubleServerHandler serverHandle;

    public static void start() {
        start(DEFAULT_PORT);
    }

    private static void start(Integer defaultPort) {
        if (null != serverHandle) {
            serverHandle.stop();
        }
        serverHandle = new NIODoubleServerHandler(defaultPort);
        new Thread(serverHandle, "Server").start();
    }


}
