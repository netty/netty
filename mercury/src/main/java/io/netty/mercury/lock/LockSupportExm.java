package io.netty.mercury.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class LockSupportExm {
    public static void main(String[] args) {
        final Thread t1 = new Thread(){
            @Override
            public void run() {
                Thread.currentThread().setName("t1");
                System.out.println(Thread.currentThread().getName()+" before park");
                LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(10,TimeUnit.SECONDS));
                System.out.println(Thread.currentThread().getName()+" after park");
            }
        };
        Thread t2 = new Thread(){
            @Override
            public void run() {
                Thread.currentThread().setName("t2");
                System.out.println(Thread.currentThread().getName()+" unpark t1");
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
//                LockSupport.unpark(t1);
                LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(10,TimeUnit.SECONDS));
            }
        };
        Thread t3 = new Thread(){
            @Override
            public void run() {
                Thread.currentThread().setName("t3");
                System.out.println(Thread.currentThread().getName()+" park 5s");
                LockSupport.parkUntil(System.currentTimeMillis()+TimeUnit.MICROSECONDS.convert(5,TimeUnit.SECONDS));
                System.out.println(Thread.currentThread().getName()+"a fter park");
            }
        };
        t1.start();
        t2.start();
        t3.start();
        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
