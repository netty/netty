package io.netty.mercury.sychronized;

public class SychronizedThread implements Runnable {

    private static int count = 0;


    public synchronized void test(String s) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.out.println(Thread.currentThread().getName() + ":" + count++);
            Thread.sleep(1000);
        }
    }

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            test(name);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
