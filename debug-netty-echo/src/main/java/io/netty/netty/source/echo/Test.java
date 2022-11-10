package io.netty.netty.source.echo;

public class Test {
    public static void main(String[] args) throws Exception {
        System.out.println(System.nanoTime());
        Thread.sleep(1000);
        System.out.println(System.nanoTime());


        int a = 10;
        int b = 20;
        int c = 10;
        c -= a - b; // c = c - (a-b) = c - a + b = 10 - 10 + 20
        System.out.println(c);
    }
}
