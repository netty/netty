package io.netty.mercury.executor;

import java.util.concurrent.*;

public class CompletionServiceExam {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newCachedThreadPool();
        CompletionService<Integer> completionService = new ExecutorCompletionService<Integer>(service);
        for (int i = 0; i < 5; i++) {
            completionService.submit(new TaskInteger(i));
        }
        service.shutdown();
        for (int i = 0; i < 5; i++) {
            Future<Integer> future = completionService.take();
            System.out.println(future.get());
        }
    }

    static class TaskInteger implements Callable<Integer> {

        private final int sum;

        public TaskInteger(int i) {
            this.sum = i;
        }

        @Override
        public Integer call() throws Exception {
            TimeUnit.SECONDS.sleep(sum * sum);
            return sum * sum;
        }
    }
}
