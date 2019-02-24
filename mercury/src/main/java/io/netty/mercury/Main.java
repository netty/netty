package io.netty.mercury;

import io.netty.mercury.sychronized.SychronizedThread;

public class Main {

    public static void main(String[] args) {
//        Thread thread1 = new Thread1("AAA");
//        Thread thread2 = new Thread1("BBB");
//        thread1.start();
//        thread2.start();
//        new Thread(new Thread2("CCC")).start();
////        new Thread(new Thread2("DDD")).start();

        SychronizedThread sychronizedThread = new SychronizedThread();
        Thread thread1 = new Thread(sychronizedThread, "a");
        Thread thread2 = new Thread(sychronizedThread, "b");
        thread1.start();
        thread2.start();


    }
}
