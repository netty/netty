package io.netty.handler.ipfilter;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.SocketUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.*;

public class UniqueIpFilterTest {

    @Test
    public void testUniqueIpFilterHandler() throws ExecutionException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        for (int round = 0; round < 10000; round++) {
            System.out.println("round = " + round);
            final UniqueIpFilter handler = new UniqueIpFilter();
            java.util.concurrent.Future<EmbeddedChannel> future1 = submit(handler, barrier, executorService);
            java.util.concurrent.Future<EmbeddedChannel> future2 = submit(handler, barrier, executorService);
            EmbeddedChannel channel1 = future1.get();
            EmbeddedChannel channel2 = future2.get();
            Assert.assertTrue(channel1.isActive() || channel2.isActive());
            Assert.assertFalse(channel1.isActive() && channel2.isActive());

            barrier.reset();
            channel1.close().await();
            channel2.close().await();
        }
    }

    private java.util.concurrent.Future<EmbeddedChannel> submit(final UniqueIpFilter handler, final CyclicBarrier barrier, ExecutorService executorService) {
        return executorService.submit(new Callable<EmbeddedChannel>() {
            @Override
            public EmbeddedChannel call() throws Exception {
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
                return new EmbeddedChannel(handler) {
                    @Override
                    protected SocketAddress remoteAddress0() {
                        return isActive() ? SocketUtils.socketAddress("91.92.93.1", 5421) : null;
                    }
                };
            }
        });
    }

}