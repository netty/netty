package io.netty.mercury.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class SimpleLock {

    private static class Sync extends AbstractQueuedSynchronizer{
        @Override
        protected boolean tryAcquire(int arg) {
            return compareAndSetState(0,1);
        }

        @Override
        protected boolean tryRelease(int arg) {
            setState(0);
            return true;
        }

        protected Sync() {
            super();
        }
    }

    private  final  Sync sync = new Sync();

    public void lock(){
        sync.acquire(1);
    }

    public  void unlock(){
        sync.release(1);
    }
    private static class MyThread extends Thread{
        private final  String name;
        private final  SimpleLock lock;

        public MyThread(String name, SimpleLock lock) {
            this.name = name;
            this.lock = lock;
        }

        @Override
        public void run() {
            lock.lock();
            System.out.println(name + " get the lock");
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                lock.unlock();
                System.out.println(name+" release the lock");
            }
        }
    }

    public static void main(String[] args) {
        final  SimpleLock mutex = new SimpleLock();
        MyThread t1 = new MyThread("t1",mutex);
        MyThread t2 = new MyThread("t2",mutex);
        MyThread t3 = new MyThread("t3",mutex);
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
        System.out.println("main thread exit");
    }
}
