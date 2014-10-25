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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TrafficShapingHandlerTest extends AbstractSocketTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TrafficShapingHandlerTest.class);
    private static final InternalLogger loggerServer = InternalLoggerFactory.getInstance("ServerTSH");
    private static final InternalLogger loggerClient = InternalLoggerFactory.getInstance("ClientTSH");

    static final int messageSize = 1024;
    static final int bandwidthFactor = 12;
    static final int minfactor = 3;
    static final int maxfactor = bandwidthFactor + bandwidthFactor / 2;
    static final long stepms = (1000 / bandwidthFactor - 10) / 10 * 10;
    static final long minimalms = Math.max(stepms / 2, 20) / 10 * 10;
    static final long check = 10;
    private static final Random random = new Random();
    static final byte[] data = new byte[messageSize];

    private static final String TRAFFIC = "traffic";
    private static String currentTestName;
    private static int currentTestRun;

    private static EventExecutorGroup group;
    private static EventExecutorGroup groupForGlobal;
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    static {
        random.nextBytes(data);
    }

    @BeforeClass
    public static void createGroup() {
        logger.info("Bandwidth: " + minfactor + " <= " + bandwidthFactor + " <= " + maxfactor +
                    " StepMs: " + stepms + " MinMs: " + minimalms + " CheckMs: " + check);
        group = new DefaultEventExecutorGroup(8);
        groupForGlobal = new DefaultEventExecutorGroup(8);
    }

    @AfterClass
    public static void destroyGroup() throws Exception {
        group.shutdownGracefully().sync();
        groupForGlobal.shutdownGracefully().sync();
        executor.shutdown();
    }

    private static long[] computeWaitRead(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        minimalWaitBetween[0] = 0;
        for (int i = 0; i < multipleMessage.length; i++) {
            if (multipleMessage[i] > 1) {
                minimalWaitBetween[i + 1] = (multipleMessage[i] - 1) * stepms + minimalms;
            } else {
                minimalWaitBetween[i + 1] = 10;
            }
        }
        return minimalWaitBetween;
    }

    private static long[] computeWaitWrite(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        for (int i = 0; i < multipleMessage.length; i++) {
            if (multipleMessage[i] > 1) {
                minimalWaitBetween[i] = (multipleMessage[i] - 1) * stepms + minimalms;
            } else {
                minimalWaitBetween[i] = 10;
            }
        }
        return minimalWaitBetween;
    }

    private static long[] computeWaitAutoRead(int []autoRead) {
        long [] minimalWaitBetween = new long[autoRead.length + 1];
        minimalWaitBetween[0] = 0;
        for (int i = 0; i < autoRead.length; i++) {
            if (autoRead[i] != 0) {
                if (autoRead[i] > 0) {
                    minimalWaitBetween[i + 1] = -1;
                } else {
                    minimalWaitBetween[i + 1] = check;
                }
            } else {
                minimalWaitBetween[i + 1] = 0;
            }
        }
        return minimalWaitBetween;
    }

    @Test(timeout = 10000)
    public void testNoTrafficShapping() throws Throwable {
        currentTestName = "TEST NO TRAFFIC";
        currentTestRun = 0;
        run();
    }

    public void testNoTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = null;
        testTrafficShapping0(sb, cb, false, false, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testWriteTrafficShapping() throws Throwable {
        currentTestName = "TEST WRITE";
        currentTestRun = 0;
        run();
    }

    public void testWriteTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testReadTrafficShapping() throws Throwable {
        currentTestName = "TEST READ";
        currentTestRun = 0;
        run();
    }

    public void testReadTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testWrite1TrafficShapping() throws Throwable {
        currentTestName = "TEST WRITE";
        currentTestRun = 0;
        run();
    }

    public void testWrite1TrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testRead1TrafficShapping() throws Throwable {
        currentTestName = "TEST READ";
        currentTestRun = 0;
        run();
    }

    public void testRead1TrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testWriteGlobalTrafficShapping() throws Throwable {
        currentTestName = "TEST GLOBAL WRITE";
        currentTestRun = 0;
        run();
    }

    public void testWriteGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testReadGlobalTrafficShapping() throws Throwable {
        currentTestName = "TEST GLOBAL READ";
        currentTestRun = 0;
        run();
    }

    public void testReadGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testAutoReadTrafficShapping() throws Throwable {
        currentTestName = "TEST AUTO READ";
        currentTestRun = 0;
        run();
    }

    public void testAutoReadTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = new int[autoRead.length];
        Arrays.fill(multipleMessage, 1);
        long[] minimalWaitBetween = computeWaitAutoRead(autoRead);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 10000)
    public void testAutoReadGlobalTrafficShapping() throws Throwable {
        currentTestName = "TEST AUTO READ GLOBAL";
        currentTestRun = 0;
        run();
    }

    public void testAutoReadGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = new int[autoRead.length];
        Arrays.fill(multipleMessage, 1);
        long[] minimalWaitBetween = computeWaitAutoRead(autoRead);
        testTrafficShapping0(sb, cb, false, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    /**
     *
     * @param additionalExecutor
     *            shall the pipeline add the handler using an additionnal executor
     * @param limitRead
     *            True to set Read Limit on Server side
     * @param limitWrite
     *            True to set Write Limit on Client side
     * @param globalLimit
     *            True to change Channel to Global TrafficShapping
     * @param minimalWaitBetween
     *            time in ms that should be waited before getting the final result (note: for READ the values are
     *            right shifted once, the first value being 0)
     * @param multipleMessage
     *            how many message to send at each step (for READ: the first should be 1, as the two last steps to
     *            ensure correct testing)
     * @throws Throwable
     */
    private static void testTrafficShapping0(ServerBootstrap sb, Bootstrap cb, final boolean additionalExecutor,
            final boolean limitRead, final boolean limitWrite, final boolean globalLimit, int[] autoRead,
            long[] minimalWaitBetween, int[] multipleMessage) throws Throwable {

        currentTestRun++;
        logger.info("TEST: " + currentTestName + " RUN: " + currentTestRun +
                    " Exec: " + additionalExecutor + " Read: " + limitRead + " Write: " + limitWrite + " Global: "
                    + globalLimit);
        final ServerHandler sh = new ServerHandler(autoRead, multipleMessage);
        Promise<Boolean> promise = group.next().newPromise();
        final ClientHandler ch = new ClientHandler(promise, minimalWaitBetween, multipleMessage,
                autoRead);

        final AbstractTrafficShapingHandler handler;
        if (limitRead) {
            if (globalLimit) {
                handler = new GlobalTrafficShapingHandler(groupForGlobal, 0, bandwidthFactor * messageSize, check);
            } else {
                handler = new ChannelTrafficShapingHandler(0, bandwidthFactor * messageSize, check);
            }
        } else if (limitWrite) {
            if (globalLimit) {
                handler = new GlobalTrafficShapingHandler(groupForGlobal, bandwidthFactor * messageSize, 0, check);
            } else {
                handler = new ChannelTrafficShapingHandler(bandwidthFactor * messageSize, 0, check);
            }
        } else {
            handler = null;
        }

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel c) throws Exception {
                if (limitRead) {
                    c.pipeline().addLast(TRAFFIC, handler);
                }
                c.pipeline().addLast(sh);
            }
        });
        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel c) throws Exception {
                if (limitWrite) {
                    c.pipeline().addLast(TRAFFIC, handler);
                }
                c.pipeline().addLast(ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        int totalNb = 0;
        for (int i = 1; i < multipleMessage.length; i++) {
            totalNb += multipleMessage[i];
        }
        Long start = TrafficCounter.milliSecondFromNano();
        int nb = multipleMessage[0];
        for (int i = 0; i < nb; i++) {
            cc.write(cc.alloc().buffer().writeBytes(data));
        }
        cc.flush();

        promise.await();
        Long stop = TrafficCounter.milliSecondFromNano();
        assertTrue("Error during exceution of TrafficShapping: " + promise.cause(), promise.isSuccess());

        float average = (totalNb * messageSize) / (float) (stop - start);
        logger.info("TEST: " + currentTestName + " RUN: " + currentTestRun +
                    " Average of traffic: " + average + " compare to " + bandwidthFactor);
        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();
        if (autoRead != null) {
            // for extra release call in AutoRead
            Thread.sleep(minimalms);
        }

        if (autoRead == null && minimalWaitBetween != null) {
            assertTrue("Overall Traffic not ok since > " + maxfactor + ": " + average,
                    average <= maxfactor);
            if (additionalExecutor) {
                // Oio is not as good when using additionalExecutor
                assertTrue("Overall Traffic not ok since < 0.25: " + average, average >= 0.25);
            } else {
                assertTrue("Overall Traffic not ok since < " + minfactor + ": " + average,
                        average >= minfactor);
            }
        }
        if (handler != null && globalLimit) {
            ((GlobalTrafficShapingHandler) handler).release();
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

    private static class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int step;
        // first message will always be validated
        private long currentLastTime = TrafficCounter.milliSecondFromNano();
        private final long[] minimalWaitBetween;
        private final int[] multipleMessage;
        private final int[] autoRead;
        final Promise<Boolean> promise;

        ClientHandler(Promise<Boolean> promise, long[] minimalWaitBetween, int[] multipleMessage,
                int[] autoRead) {
            this.minimalWaitBetween = minimalWaitBetween;
            this.multipleMessage = Arrays.copyOf(multipleMessage, multipleMessage.length);
            this.promise = promise;
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            long lastTimestamp = 0;
            loggerClient.debug("Step: " + step + " Read: " + in.readableBytes() / 8 + " blocks");
            while (in.isReadable()) {
                lastTimestamp = in.readLong();
                multipleMessage[step]--;
            }
            if (multipleMessage[step] > 0) {
                // still some message to get
                return;
            }
            long minimalWait = minimalWaitBetween != null? minimalWaitBetween[step] : 0;
            int ar = 0;
            if (autoRead != null) {
                if (step > 0 && autoRead[step - 1] != 0) {
                    ar = autoRead[step - 1];
                }
            }
            loggerClient.info("Step: " + step + " Interval: " + (lastTimestamp - currentLastTime) + " compareTo "
                              + minimalWait + " (" + ar + ')');
            assertTrue("The interval of time is incorrect:" + (lastTimestamp - currentLastTime) + " not> "
                    + minimalWait, lastTimestamp - currentLastTime >= minimalWait);
            currentLastTime = lastTimestamp;
            step++;
            if (multipleMessage.length > step) {
                int nb = multipleMessage[step];
                for (int i = 0; i < nb; i++) {
                    channel.write(channel.alloc().buffer().writeBytes(data));
                }
                channel.flush();
            } else {
                promise.setSuccess(true);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                promise.setFailure(cause);
                ctx.close();
            }
        }
    }

    private static class ServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final int[] autoRead;
        private final int[] multipleMessage;
        volatile Channel channel;
        volatile int step;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        ServerHandler(int[] autoRead, int[] multipleMessage) {
            this.autoRead = autoRead;
            this.multipleMessage = Arrays.copyOf(multipleMessage, multipleMessage.length);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(final ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            int nb = actual.length / messageSize;
            loggerServer.info("Step: " + step + " Read: " + nb + " blocks");
            in.readBytes(actual);
            long timestamp = TrafficCounter.milliSecondFromNano();
            int isAutoRead = 0;
            int laststep = step;
            for (int i = 0; i < nb; i++) {
                multipleMessage[step]--;
                if (multipleMessage[step] == 0) {
                    // setAutoRead test
                    if (autoRead != null) {
                        isAutoRead = autoRead[step];
                    }
                    step++;
                }
            }
            if (laststep != step) {
                // setAutoRead test
                if (autoRead != null && isAutoRead != 2) {
                    if (isAutoRead != 0) {
                        loggerServer.info("Step: " + step + " Set AutoRead: " + (isAutoRead > 0));
                        channel.config().setAutoRead(isAutoRead > 0);
                    } else {
                        loggerServer.info("Step: " + step + " AutoRead: NO");
                    }
                }
            }
            Thread.sleep(10);
            loggerServer.debug("Step: " + step + " Write: " + nb);
            for (int i = 0; i < nb; i++) {
                channel.write(Unpooled.copyLong(timestamp));
            }
            channel.flush();
            if (laststep != step) {
                // setAutoRead test
                if (isAutoRead != 0) {
                    if (isAutoRead < 0) {
                        final int exactStep = step;
                        long wait = isAutoRead == -1? minimalms : stepms + minimalms;
                        if (isAutoRead == -3) {
                            wait = stepms * 3;
                        }
                        executor.schedule(new Runnable() {
                            @Override
                            public void run() {
                                loggerServer.info("Step: " + exactStep + " Reset AutoRead");
                                channel.config().setAutoRead(true);
                            }
                        }, wait, TimeUnit.MILLISECONDS);
                    } else {
                        if (isAutoRead > 1) {
                            loggerServer.debug("Step: " + step + " Will Set AutoRead: True");
                            final int exactStep = step;
                            executor.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    loggerServer.info("Step: " + exactStep + " Set AutoRead: True");
                                    channel.config().setAutoRead(true);
                                }
                            }, stepms + minimalms, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
