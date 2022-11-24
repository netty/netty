package io.netty.netty.heartbeat;

public class Test {
    public static void main(String[] args) throws Exception {

        System.out.println(System.nanoTime()); // 纳秒  10亿分之1
        Thread.sleep(1000);
        System.out.println(System.nanoTime());

    }
}
