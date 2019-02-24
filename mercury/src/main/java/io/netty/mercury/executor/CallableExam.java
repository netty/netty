package io.netty.mercury.executor;

import java.util.concurrent.*;

public class CallableExam {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newCachedThreadPool();
        Future<Integer> future = service.submit(
                new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        System.out.println("callable is running");
                        TimeUnit.SECONDS.sleep(2);
                        return 46;
                    }
                }
        );
        service.shutdown();
        System.out.println(future.get());
    }
}
