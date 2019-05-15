package com.lhh.day04;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copyright (C), 2019-2019
 * FileName: SubReactor
 * Author:   s·D·bs
 * Date:     2019/5/15 23:11
 * Description: 多work线程处理读写业务逻辑
 * Motto: 0.45%
 *
 * @create 2019/5/15
 * @since 1.0.0
 */
public class SubReactor implements Runnable {

    final Selector mySelector;

    //多线程处理业务逻辑
    int workCount = Runtime.getRuntime().availableProcessors();
    ExecutorService executorService = Executors.newFixedThreadPool(workCount);

    public SubReactor() throws IOException {
        // 每个SubReactor 一个selector  (多个)
        this.mySelector = SelectorProvider.provider().openSelector();
    }


    /**
     * 功能描述: <br>
     * 〈注册chanel〉
     *
     * @param:
     * @return:void
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/15 23:20
     */
    public void registerChannel(SocketChannel socketChannel) throws Exception {
        socketChannel.register(mySelector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT);
    }

    @Override
    public void run() {
        while (true) {
            //每个SubReactor 自己做事件分派处理读写事件
            try {
                mySelector.select();
                Set<SelectionKey> keys = mySelector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isReadable()) {
                        read();
                    } else if (key.isWritable()) {
                        write();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void read() {
        //任务异步处理
        executorService.submit(this::process);
    }

    private void process() {
        System.out.println(" MultiWorkThreadAcceptor -> SubReactor -> task 业务处理");
    }

    private void write() {
        //任务异步处理
        executorService.submit(this::process);
    }
}
