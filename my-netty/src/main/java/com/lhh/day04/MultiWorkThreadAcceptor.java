package com.lhh.day04;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * Copyright (C), 2019-2019
 * FileName: MultiWorkThreadAcceptor
 * Author:   s·D·bs
 * Date:     2019/5/15 23:07
 * Description: 多 Reactor 多线程模型
 * 多work 连接事件Acceptor,处理连接事件
 * <p>
 * mainReactor 主要用来处理网络 IO 连接建立操作，
 * 通常，mainReactor 只需要一个，因为它一个线程就可以处理。
 * subReactor 主要和建立起来的客户端的 SocketChannel 做数据交互和事件业务处理操作。
 * 通常，subReactor 的个数和 CPU 个数相等，每个 subReactor 独占一个线程来处理。
 * 每个模块的工作更加专一，耦合度更低，性能和稳定性也大大的提升，支持的可并发客户端数量可达到上百万级别
 * <p>
 * Motto: 0.45%
 *
 * @create 2019/5/15
 * @since 1.0.0
 */

@Slf4j
public class MultiWorkThreadAcceptor implements Runnable {
    // cpu线程数相同多work线程
    int workCount = Runtime.getRuntime().availableProcessors();
    //抽离出来的处理业务逻辑代码
    SubReactor[] workThreadHandlers = new SubReactor[workCount];
    volatile int nextHandler = 0;
    final SocketChannel socketChannel;

    public MultiWorkThreadAcceptor(ServerSocket serverSocket, SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.init();
    }

    private void init() {
        nextHandler = 0;
        for (int i = 0; i < workThreadHandlers.length; i++) {
            try {
                workThreadHandlers[i] = new SubReactor();
            } catch (Exception e) {
                log.error("MultiWorkThreadAcceptor is error:{}", e);
            }
        }
    }

    @Override
    public void run() {
        try {

            if (socketChannel != null) {// 注册读写
                synchronized (socketChannel) {
                    // 顺序获取SubReactor，然后注册channel
                    SubReactor work = workThreadHandlers[nextHandler];
                    work.registerChannel(socketChannel);
                    nextHandler++;
                    if (nextHandler >= workThreadHandlers.length) {
                        nextHandler = 0;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
