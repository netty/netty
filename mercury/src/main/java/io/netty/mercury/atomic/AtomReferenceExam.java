package io.netty.mercury.atomic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AtomReferenceExam {
    public static void main(String[] args) throws InterruptedException {
        final AtomicReference<Element> reference = new AtomicReference<Element>(new Element(10,10));
        ExecutorService service = Executors.newCachedThreadPool();
        for (int i = 0; i < 10; i++) {
            service.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            for (int j = 0; j < 10000; j++) {
                                boolean flag = false;

                                //zixuansuo
                                while (!flag){
                                    Element oldElement = reference.get();
                                    Element newElement = new Element(oldElement.x+1,oldElement.y+1);
                                    flag= reference.compareAndSet(oldElement,newElement);
                                }
                            }
                        }
                    }
            );
        }
        service.shutdown();
        service.awaitTermination(1, TimeUnit.DAYS);
        System.out.println("element.x="+reference.get().x+"\nelement.y="+reference.get().y);
    }
}
