package io.netty.mercury.atomic;

public class Test {
    private final int x;

    public Test(int x) {
        this.x = x;
        System.out.println("test ctlr");

    }
    int getX(){
        return x;
    }
}
