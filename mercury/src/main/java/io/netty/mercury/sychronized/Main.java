package io.netty.mercury.sychronized;

public class Main {
    public static void main(String[] args) {
        StaticSynchroTread run1 = new StaticSynchroTread();
        StaticSynchroTread run2 = new StaticSynchroTread();
        Thread thread1 = new Thread(run1);
        Thread thread2 = new Thread(run2);
        thread1.start();
        thread2.start();
    }
}
