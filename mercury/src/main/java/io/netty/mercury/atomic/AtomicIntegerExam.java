package io.netty.mercury.atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerExam {
    public static void main(String[] args) throws InterruptedException {
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        ExecutorService service = Executors.newCachedThreadPool();
        for (int i = 0; i < 10 ; i++) {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 10000 ; j++) {
                        atomicInteger.incrementAndGet();
                    }
                }
            });
        }
        service.shutdown();
        while (!service.awaitTermination(1, TimeUnit.MILLISECONDS)){
            System.out.println("service executing----- ");
        }
        System.out.println(atomicInteger.get());
    }
}
