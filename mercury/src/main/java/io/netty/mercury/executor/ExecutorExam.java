package io.netty.mercury.executor;

import io.netty.mercury.thread.Thread2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorExam {

    public static void main(String[] args) {
        ExecutorService service = Executors.newCachedThreadPool();
        service.execute(new Thread2("thread1"));
        service.execute(new Thread2("thread2"));
        service.execute(new Thread2("thread3"));
        service.shutdown();
    }
}
