package io.netty.mercury.thread;

public class Thread1 extends Thread {

    private String name;

    public Thread1(String name) {
        this.name = name;
    }

    @Override
    public void run() {
        for (int i = 0; i < 5; i++) {
//            System.out.println( name+":"+i+" is running.");
            try {
                System.out.println(name + " thread:" + Thread.interrupted());
                Thread.sleep(10000);
                System.out.println(name + " thread:" + Thread.interrupted());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}
