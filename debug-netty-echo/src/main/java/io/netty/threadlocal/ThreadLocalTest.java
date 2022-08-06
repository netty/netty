package io.netty.threadlocal;

/**
 * @author lxcecho 909231497@qq.com
 * @since 22:01 06-08-2022
 */
public class ThreadLocalTest {

    private static final ThreadLocal<String> THREAD_NAME_LOCAL = ThreadLocal.withInitial(() -> Thread.currentThread().getName());

    private static final ThreadLocal<TradeOrder> TRADE_THREAD_LOCAL = new ThreadLocal<TradeOrder>();

    public static void main(String[] args) {
        for (int i = 0; i < 2; i++) {
            int tradeId = i;
            new Thread(() -> {
                TradeOrder tradeOrder = new TradeOrder(tradeId, tradeId % 2 == 0 ? "已支付" : "未支付");
                TRADE_THREAD_LOCAL.set(tradeOrder);
                System.out.println("threadName: "+ THREAD_NAME_LOCAL.get());
                System.out.println("tradeOrder info: "+ TRADE_THREAD_LOCAL.get());
            }, "thread-" + i).start();
        }
    }


    static class TradeOrder {
        long id;

        String status;

        public TradeOrder(long id, String status) {
            this.id = id;
            this.status = status;
        }

        @Override
        public String toString() {
            return "TradeOrder{" +
                    "id=" + id +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

}
