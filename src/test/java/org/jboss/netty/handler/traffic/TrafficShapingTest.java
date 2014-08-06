/*
 * Copyright 2012 The Netty Project
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.traffic;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TrafficShapingTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TrafficShapingTest.class);

    static final int messageSize = 1024;
    static final int bandwidthFactor = 4;
    static final int check = 100;
    private static final Random random = new Random();
    static final byte[] data = new byte[messageSize];

    private static ExecutorService group = Executors.newCachedThreadPool();
    private static Timer timer = new HashedWheelTimer(20, TimeUnit.MILLISECONDS);
    private static OrderedMemoryAwareThreadPoolExecutor executorHandler =
            new OrderedMemoryAwareThreadPoolExecutor(10, 1000000, 10000000);
    static {
        random.nextBytes(data);
    }

    private static ServerBootstrap bootstrapServer;
    private static ClientBootstrap bootstrapCient;
    private static InetSocketAddress serverSocketAddress;
    private static Channel serverChannel;
    
    @BeforeClass
    public static void createGroup() {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.INFO);
        //logger.setLevel(Level.DEBUG);
        serverSocketAddress = new InetSocketAddress("127.0.0.1", 0);

        bootstrapServer = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        group, group));
        bootstrapServer.setOption("localAddress", serverSocketAddress);
        bootstrapServer.setOption("child.tcpNoDelay", true);
        bootstrapServer.setOption("child.reuseAddress", true);
        bootstrapServer.setOption("child.receiveBufferSize", 1048576);
        bootstrapServer.setOption("child.sendBufferSize", 1048576);
        bootstrapServer.setOption("tcpNoDelay", true);
        bootstrapServer.setOption("reuseAddress", true);
        bootstrapServer.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return getPipelineTraffic(true);
            }
        });
        System.out.println("Try bind");
        serverChannel = bootstrapServer.bind();
        serverSocketAddress = (InetSocketAddress) serverChannel.getLocalAddress();
        System.out.println("Binded: "+serverSocketAddress);

        bootstrapCient = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        group, group));
        bootstrapCient.setOption("tcpNoDelay", true);
        bootstrapCient.setOption("reuseAddress", true);
        bootstrapCient.setOption("receiveBufferSize", 1048576);
        bootstrapCient.setOption("sendBufferSize", 1048576);
        bootstrapCient.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                return getPipelineTraffic(false);
            }
        });
    }

    @AfterClass
    public static void destroyGroup() throws Exception {
        serverChannel.close();
        bootstrapServer.shutdown();
        bootstrapCient.shutdown();
        group.shutdown();
    }

    private static int []autoRead;
    private static int []multipleMessage;
    private static long []minimalWaitBetween;
    private static boolean limitRead, globalLimit, limitWrite, additionalExecutor;
    private static ChannelFuture promise;
    private static ValidTimestampedHandler sh;
    private static ClientTrafficHandler ch;
    private static AbstractTrafficShapingHandler handler;

    private static ChannelPipeline getPipelineTraffic(boolean server) {
        if (server) {
            sh = new ValidTimestampedHandler(autoRead, multipleMessage);
            ChannelPipeline p = Channels.pipeline();
            if (limitRead) {
                if (globalLimit) {
                    handler = new GlobalTrafficShapingHandler(timer, 0, bandwidthFactor * messageSize, check);
                } else {
                    handler = new ChannelTrafficShapingHandler(timer, 0, bandwidthFactor * messageSize, check);
                }
                p.addLast("traffic", handler);
            }
            if (additionalExecutor) {
                p.addLast("executor", new ExecutionHandler(executorHandler));
            }
            p.addLast("handler", sh);
            logger.info("Server Pipeline: "+p);
            return p;
        } else {
            ch = new ClientTrafficHandler(promise, minimalWaitBetween, multipleMessage,
                    autoRead);
            ChannelPipeline p = Channels.pipeline();
            if (limitWrite) {
                if (globalLimit) {
                    handler = new GlobalTrafficShapingHandler(timer, bandwidthFactor * messageSize, 0, check);
                } else {
                    handler = new ChannelTrafficShapingHandler(timer, bandwidthFactor * messageSize, 0, check);
                }
                p.addLast("traffic", handler);
            }
            if (additionalExecutor) {
                p.addLast("executor", new ExecutionHandler(executorHandler));
            }
            p.addLast("handler", ch);
            logger.info("Client Pipeline: "+p);
            return p;
        }
    }

    private static long[] computeWaitRead(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        minimalWaitBetween[0] = 0;
        for (int i = 0; i < multipleMessage.length; i++) {
            minimalWaitBetween[i + 1] = (multipleMessage[i] - 1) * (1000 / bandwidthFactor)
                    + (1000 / bandwidthFactor - 100);
        }
        return minimalWaitBetween;
    }

    private static long[] computeWaitWrite(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        for (int i = 0; i < multipleMessage.length; i++) {
            minimalWaitBetween[i] = (multipleMessage[i] - 1) * (1000 / bandwidthFactor)
                    + (1000 / bandwidthFactor - 150);
        }
        minimalWaitBetween[0] = 0;
        return minimalWaitBetween;
    }

    @Test(timeout = 30000)
    public void testNoTrafficShapping() throws Throwable {
        logger.info("TEST NO TRAFFIC");
        testNoTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testNoTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = null;
        testTrafficShapping0(sb, cb, false, false, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecNoTrafficShapping() throws Throwable {
        logger.info("TEST EXEC NO TRAFFIC");
        testExecNoTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testExecNoTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = null;
        testTrafficShapping0(sb, cb, true, false, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWriteTrafficShapping() throws Throwable {
        logger.info("TEST WRITE");
        testWriteTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testWriteTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testReadTrafficShapping() throws Throwable {
        logger.info("TEST READ");
        testReadTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testReadTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWrite1TrafficShapping() throws Throwable {
        logger.info("TEST WRITE");
        testWrite1TrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testWrite1TrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testRead1TrafficShapping() throws Throwable {
        logger.info("TEST READ");
        testRead1TrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testRead1TrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecWriteTrafficShapping() throws Throwable {
        logger.info("TEST EXEC WRITE");
        testExecWriteTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testExecWriteTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, true, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecReadTrafficShapping() throws Throwable {
        logger.info("TEST EXEC READ");
        testExecReadTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testExecReadTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, true, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWriteGlobalTrafficShapping() throws Throwable {
        logger.info("TEST GLOBAL WRITE");
        testWriteGlobalTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testWriteGlobalTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testReadGlobalTrafficShapping() throws Throwable {
        logger.info("TEST GLOBAL READ");
        testReadGlobalTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testReadGlobalTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 60000)
    public void testAutoReadTrafficShapping() throws Throwable {
        logger.info("TEST AUTO READ");
        testAutoReadTrafficShapping(bootstrapServer, bootstrapCient);
    }

    public void testAutoReadTrafficShapping(ServerBootstrap sb, ClientBootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, -3, 0, 1, 2, 0 };
        int[] multipleMessage = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    /**
     *
     * @param sb
     * @param cb
     * @param additionalExecutor
     *            shall the pipeline add the handler using an additionnal executor
     * @param limitRead
     *            True to set Read Limit on Server side
     * @param limitWrite
     *            True to set Write Limit on Client side
     * @param globalLimit
     *            True to change Channel to Global TrafficShapping
     * @param autoRead
     * @param minimalWaitBetween
     *            time in ms that should be waited before getting the final result (note: for READ the values are
     *            right shifted once, the first value being 0)
     * @param multipleMessage
     *            how many message to send at each step (for READ: the first should be 1, as the two last steps to
     *            ensure correct testing)
     * @throws Throwable
     */
    private static void testTrafficShapping0(ServerBootstrap sb, ClientBootstrap cb, final boolean additionalExecutor,
            final boolean limitRead, final boolean limitWrite, final boolean globalLimit, int[] autoRead,
            long[] minimalWaitBetween, int[] multipleMessage) throws Throwable {
        logger.info("Exec: " + additionalExecutor + " Read: " + limitRead + " Write: " + limitWrite + " Global: "
                + globalLimit);
        TrafficShapingTest.additionalExecutor = additionalExecutor;
        TrafficShapingTest.autoRead = autoRead;
        TrafficShapingTest.globalLimit = globalLimit;
        TrafficShapingTest.limitRead = limitRead;
        TrafficShapingTest.limitWrite = limitWrite;
        TrafficShapingTest.minimalWaitBetween = minimalWaitBetween;
        TrafficShapingTest.multipleMessage = multipleMessage;
        TrafficShapingTest.promise = new DefaultChannelFuture(null, true);

        Channel cc = cb.connect(serverSocketAddress).await().getChannel();

        int totalNb = 0;
        for (int i = 1; i < multipleMessage.length; i++) {
            totalNb += multipleMessage[i];
        }
        Long start = System.currentTimeMillis();
        int nb = multipleMessage[0];
        for (int i = 0; i < nb; i++) {
            cc.write(ChannelBuffers.wrappedBuffer(data));
        }

        promise.await();
        Long stop = System.currentTimeMillis();
        assertTrue("Error during exceution of TrafficShapping: " + promise.getCause(), promise.isSuccess());

        float average = (totalNb * messageSize) / (float) (stop - start);
        logger.info("Average of traffic: " + average + " compare to " + bandwidthFactor);
        
        sh.channel.close().await();
        ch.channel.close().await();

        // for extra release call in AutoRead
        Thread.sleep(500);

        if (autoRead == null && minimalWaitBetween != null) {
            assertTrue("Overall Traffic not ok since > " + (bandwidthFactor + 1) + ": " + average,
                    average <= bandwidthFactor + 1);
            /*if (additionalExecutor) {
                // Oio is not as good when using additionalExecutor
                assertTrue("Overall Traffic not ok since < 0.25: " + average, average >= 0.25);
            } else {
                assertTrue("Overall Traffic not ok since < " + (bandwidthFactor - 1.5) + ": " + average,
                        average >= bandwidthFactor - 1.5);
            }*/
            assertTrue("Overall Traffic not ok since < " + (bandwidthFactor - 1.5) + ": " + average,
                    average >= bandwidthFactor - 1.5);
        }
        if (handler != null && globalLimit) {
            ((GlobalTrafficShapingHandler) handler).releaseExternalResources();
        }

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    private static class ClientTrafficHandler extends SimpleChannelHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int step;
        // first message will always be validated
        private long currentLastTime = System.currentTimeMillis();
        private final long[] minimalWaitBetween;
        private final int[] multipleMessage;
        private final int[] autoRead;
        final ChannelFuture promise;

        ClientTrafficHandler(ChannelFuture promise, long[] minimalWaitBetween, int[] multipleMessage,
                int[] autoRead) {
            this.minimalWaitBetween = minimalWaitBetween;
            if (multipleMessage != null) {
                this.multipleMessage = Arrays.copyOf(multipleMessage, multipleMessage.length);
            } else {
                this.multipleMessage = null;
            }
            this.promise = promise;
            this.autoRead = autoRead;
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
            logger.debug("C Connected");
            channel = ctx.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            channel = ctx.getChannel();
            long lastTimestamp = 0;
            ChannelBuffer in = (ChannelBuffer) e.getMessage();
            while (in.readable()) {
                lastTimestamp = in.readLong();
                multipleMessage[step]--;
            }
            if (multipleMessage[step] > 0) {
                // still some message to get
                return;
            }
            long minimalWait = (minimalWaitBetween != null) ? minimalWaitBetween[step] : 0;
            int ar = 0;
            if (autoRead != null) {
                if (step > 0 && autoRead[step - 1] != 0) {
                    ar = autoRead[step - 1];
                    if (ar > 0) {
                        minimalWait = -1;
                        if (ar == 2) {
                            minimalWait = 1000 / bandwidthFactor + 100;
                        }
                    } else {
                        minimalWait = 100;
                        if (ar == -3) {
                            minimalWait = 1000 / bandwidthFactor + 100;
                        }
                    }
                } else {
                    minimalWait = 0;
                }
            }
            logger.info("Step: " + step + " Interval: " + (lastTimestamp - currentLastTime) + " compareTo "
                    + minimalWait + " (" + ar + ")");
            assertTrue("The interval of time is incorrect:" + (lastTimestamp - currentLastTime) + " not> "
                    + minimalWait, lastTimestamp - currentLastTime >= minimalWait);
            currentLastTime = lastTimestamp;
            step++;
            if (multipleMessage.length > step) {
                int nb = multipleMessage[step];
                for (int i = 0; i < nb; i++) {
                    channel.write(ChannelBuffers.wrappedBuffer(data));
                }
            } else {
                promise.setSuccess();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getCause().printStackTrace();
                promise.setFailure(e.getCause());
                ctx.getChannel().close();
            }
        }
    }

    private static class ValidTimestampedHandler extends SimpleChannelHandler {
        private final int[] autoRead;
        private final int[] multipleMessage;
        volatile Channel channel;
        volatile int step;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        ValidTimestampedHandler(int[] autoRead, int[] multipleMessage) {
            this.autoRead = autoRead;
            if (multipleMessage != null) {
                this.multipleMessage = Arrays.copyOf(multipleMessage, multipleMessage.length);
            } else {
                this.multipleMessage = null;
            }
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
            logger.debug("S Connected");
            channel = ctx.getChannel();
        }

        @Override
        public void messageReceived(final ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            ChannelBuffer in = (ChannelBuffer) e.getMessage();
            channel = ctx.getChannel();
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);
            long timestamp = System.currentTimeMillis();
            int nb = actual.length / messageSize;
            int isAutoRead = 0;
            for (int i = 0; i < nb; i++) {
                multipleMessage[step]--;
                if (multipleMessage[step] == 0) {
                    if (autoRead != null) {
                        isAutoRead = autoRead[step];
                    }
                    step++;
                }
            }
            if (autoRead != null && isAutoRead != 2 && isAutoRead != -3) {
                if (isAutoRead != 0) {
                    logger.info("Set AutoRead: " + (isAutoRead > 0) + " Step: " + step);
                    channel.setReadable(isAutoRead > 0);
                } else {
                    logger.info("AutoRead: NO Step:" + step);
                }
            }
            logger.debug("Get: " + actual.length + " TS " + timestamp + " NB: " + nb);
            ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
            for (int i = 0; i < nb; i++) {
                buf.writeLong(timestamp);
            }
            channel.write(buf);
            if (isAutoRead != 0) {
                if (isAutoRead < 0) {
                    final int exactStep = step;
                    int wait = (isAutoRead == -1) ? 100 : 1000 / bandwidthFactor + 100;
                    if (isAutoRead == -3) {
                        Thread.sleep(wait);
                        logger.info("Set AutoRead: True for long time, Step: " + step);
                        channel.setReadable(isAutoRead > 0);
                        wait = 1000;
                    }
                    timer.newTimeout(new TimerTask() {
                        public void run(Timeout timeout) throws Exception {
                            logger.info("Reset AutoRead: Step " + exactStep);
                            channel.setReadable(true);
                        }
                    }, wait, TimeUnit.MILLISECONDS);
                } else {
                    if (isAutoRead > 1) {
                        Thread.sleep(1000 / bandwidthFactor + 100);
                        logger.info("AutoRead: " + isAutoRead + " Step:" + step);
                        channel.setReadable(isAutoRead > 0);
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getCause().printStackTrace();
                ctx.getChannel().close();
            }
        }
    }
}
