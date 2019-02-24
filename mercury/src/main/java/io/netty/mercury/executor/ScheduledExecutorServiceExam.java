package io.netty.mercury.executor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ScheduledExecutorServiceExam {

    public static void main(String[] args) {
        final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(2);
        final ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("beep");
                    }
                }, 1, 1, TimeUnit.SECONDS);

        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                System.out.println("cancel beep");
                scheduledFuture.cancel(true);
                scheduler.shutdown();
            }
        }, 10, TimeUnit.SECONDS);
    }
}
