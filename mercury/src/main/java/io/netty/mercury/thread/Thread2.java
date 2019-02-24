package io.netty.mercury.thread;

public class Thread2 implements Runnable {
    private String name;

    public Thread2(String name) {
        this.name = name;
    }

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
            System.out.println(name + ":" + i + " is running.");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
