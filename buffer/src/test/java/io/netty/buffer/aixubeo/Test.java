package io.netty.buffer.aixubeo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public class Test {

    public void test1(){
        System.out.println("----");
        PooledByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        ByteBuf buffer = allocator.directBuffer(10240);//10k
        buffer.setByte(0,68);
        System.out.println("----"+buffer.getByte(0));

    }

    public static void main(String[] args){
        Test test = new Test();
        test.test1();
    }
}
