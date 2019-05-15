

package com.lhh.day04;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copyright (C), 2019-2019
 * FileName: MultiThreadHandler
 * Author:   s·D·bs
 * Date:     2019/5/15 22:52
 * Description: 多线程处理读写业务逻辑
 * Motto: 0.45%
 *
 * @create 2019/5/15
 * @since 1.0.0
 */

public class MultiThreadHandler implements Runnable {

    public static final int READING = 0, WRITING = 1;
    int state;
    final SocketChannel socketChannel;
    final SelectionKey sk;

    // 多线程处理业务逻辑
    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    public MultiThreadHandler(SocketChannel socketChannel, Selector selector) throws Exception {
        this.state = READING;
        this.socketChannel = socketChannel;
        sk = socketChannel.register(selector, SelectionKey.OP_READ);
        sk.attach(this);
        socketChannel.configureBlocking(false);
    }


    @Override
    public void run() {
        if (state == READING) {
            read();
        } else if (state == WRITING) {
            write();
        }
    }

    private void write() {

        //任务异步处理
        executorService.submit(this::process);
        //下一步处理读事件
        sk.interestOps(SelectionKey.OP_READ);
        this.state = READING;
    }

    private void read() {
        //任务异步处理
        executorService.submit(this::process);
        //下一步处理写事件
        sk.interestOps(SelectionKey.OP_WRITE);
        this.state = WRITING;
    }

    private void process() {
        System.out.println("进行了业务处理");
    }


}
