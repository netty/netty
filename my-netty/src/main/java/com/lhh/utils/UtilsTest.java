/**
 * Copyright (C), 2019-2019
 * FileName: UtilsTest
 * Author:   s·D·bs
 * Date:     2019/4/23 0:49
 * Description: 测试小想法
 * Motto: 0.45%
 *
 * @create 2019/4/23
 * @since 1.0.0
 */


package com.lhh.utils;

import java.util.concurrent.TimeUnit;

public class UtilsTest implements Runnable {
    private volatile boolean started = true;

    public void stop() {
        started = false;
    }

    public boolean getStared() {
        if (!started) {
            System.out.println("stop ed ! ");
        }
        return started;
    }

    public void doIt() throws InterruptedException {
        while (getStared()) {
            TimeUnit.SECONDS.sleep(1);
            System.out.println("started ! ");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        UtilsTest utilsTest = new UtilsTest();
        new Thread(utilsTest).start();
        TimeUnit.SECONDS.sleep(2);
        utilsTest.stop();
    }

    @Override
    public void run() {
        try {
            if (started) {
                doIt();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("started run = " + started);
    }
}

