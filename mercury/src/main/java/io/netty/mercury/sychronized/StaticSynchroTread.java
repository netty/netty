package io.netty.mercury.sychronized;

public class StaticSynchroTread implements Runnable {

    private static int count = 1;

    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        try {
            methodA(name);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void methodA(String name) throws InterruptedException {
        for (int j = 0; j < 10; j++) {
            System.out.println(Thread.currentThread().getName() + ":" + count++);
            Thread.sleep(1000);
        }
    }

    public void methodB(String name) throws InterruptedException {
        synchronized (SychronizedThread.class) {
            for (int j = 0; j < 10; j++) {
                System.out.println(Thread.currentThread().getName() + ":" + count++);
                Thread.sleep(1000);
            }
        }
    }
}
