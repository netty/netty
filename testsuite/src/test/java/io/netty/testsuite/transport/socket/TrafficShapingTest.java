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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class TrafficShapingTest extends AbstractSocketTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TrafficShapingTest.class);
    private static final InternalLogger loggerServer = InternalLoggerFactory.getInstance(ValidTimestampedHandler.class);
    private static final InternalLogger loggerClient = InternalLoggerFactory.getInstance(ClientTrafficHandler.class);

    static final int messageSize = 1024;
    static final int bandwidthFactor = 4;
    static final int check = 100;
    private static final Random random = new Random();
    static final byte[] data = new byte[messageSize];

    private static final String TRAFFIC = "traffic";

    private static EventExecutorGroup group;
    private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    static {
        random.nextBytes(data);
    }

    @BeforeClass
    public static void createGroup() {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.INFO);
        group = new DefaultEventExecutorGroup(8);
    }

    @AfterClass
    public static void destroyGroup() throws Exception {
        group.shutdownGracefully().sync();
    }

    private static long[] computeWaitRead(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        minimalWaitBetween[0] = 0;
        for (int i = 0; i < multipleMessage.length; i++) {
            minimalWaitBetween[i + 1] = (multipleMessage[i] - 1) * (1000 / bandwidthFactor)
                    + (1000 / bandwidthFactor - 150);
        }
        return minimalWaitBetween;
    }

    private static long[] computeWaitWrite(int[] multipleMessage) {
        long[] minimalWaitBetween = new long[multipleMessage.length + 1];
        for (int i = 0; i < multipleMessage.length; i++) {
            minimalWaitBetween[i] = (multipleMessage[i] - 1) * (1000 / bandwidthFactor)
                    + (1000 / bandwidthFactor - 150);
        }
        return minimalWaitBetween;
    }

    @Test(timeout = 30000)
    public void testNoTrafficShapping() throws Throwable {
        logger.info("TEST NO TRAFFIC");
        run();
    }

    public void testNoTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = null;
        testTrafficShapping0(sb, cb, false, false, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecNoTrafficShapping() throws Throwable {
        logger.info("TEST EXEC NO TRAFFIC");
        run();
    }

    public void testExecNoTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = null;
        testTrafficShapping0(sb, cb, true, false, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWriteTrafficShapping() throws Throwable {
        logger.info("TEST WRITE");
        run();
    }

    public void testWriteTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testReadTrafficShapping() throws Throwable {
        logger.info("TEST READ");
        run();
    }

    public void testReadTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWrite1TrafficShapping() throws Throwable {
        logger.info("TEST WRITE");
        run();
    }

    public void testWrite1TrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testRead1TrafficShapping() throws Throwable {
        logger.info("TEST READ");
        run();
    }

    public void testRead1TrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecWriteTrafficShapping() throws Throwable {
        logger.info("TEST EXEC WRITE");
        run();
    }

    public void testExecWriteTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, true, false, true, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testExecReadTrafficShapping() throws Throwable {
        logger.info("TEST EXEC READ");
        run();
    }

    public void testExecReadTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, true, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testWriteGlobalTrafficShapping() throws Throwable {
        logger.info("TEST GLOBAL WRITE");
        run();
    }

    public void testWriteGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1 };
        long[] minimalWaitBetween = computeWaitWrite(multipleMessage);
        testTrafficShapping0(sb, cb, false, false, true, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 30000)
    public void testReadGlobalTrafficShapping() throws Throwable {
        logger.info("TEST GLOBAL READ");
        run();
    }

    public void testReadGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = null;
        int[] multipleMessage = { 1, 2, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
    }

    @Test(timeout = 60000)
    public void testAutoReadTrafficShapping() throws Throwable {
        logger.info("TEST AUTO READ");
        run();
    }

    public void testAutoReadTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }
    @Test(timeout = 60000)
    public void testAutoReadGlobalTrafficShapping() throws Throwable {
        logger.info("TEST AUTO READ GLOBAL");
        run();
    }

    public void testAutoReadGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, false, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
    }
    @Test(timeout = 60000)
    public void testAutoReadExecTrafficShapping() throws Throwable {
        logger.info("TEST AUTO READ EXEC");
        run();
    }

    public void testAutoReadExecTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, true, true, false, false, autoRead, minimalWaitBetween, multipleMessage);
    }
    @Test(timeout = 60000)
    public void testAutoReadExecGlobalTrafficShapping() throws Throwable {
        logger.info("TEST AUTO READ EXEC GLOBAL");
        run();
    }

    public void testAutoReadExecGlobalTrafficShapping(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        int[] autoRead = { 1, -1, -1, 1, -2, 0, 1, 0, -3, 0, 1, 2, 0 };
        int[] multipleMessage = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
        long[] minimalWaitBetween = computeWaitRead(multipleMessage);
        testTrafficShapping0(sb, cb, true, true, false, true, autoRead, minimalWaitBetween, multipleMessage);
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
    private static void testTrafficShapping0(ServerBootstrap sb, Bootstrap cb, final boolean additionalExecutor,
            final boolean limitRead, final boolean limitWrite, final boolean globalLimit, int[] autoRead,
            long[] minimalWaitBetween, int[] multipleMessage) throws Throwable {
        logger.info("Exec: " + additionalExecutor + " Read: " + limitRead + " Write: " + limitWrite + " Global: "
                + globalLimit);
        final ValidTimestampedHandler sh = new ValidTimestampedHandler(autoRead, multipleMessage);
        Promise<Boolean> promise = group.next().newPromise();
        final ClientTrafficHandler ch = new ClientTrafficHandler(promise, minimalWaitBetween, multipleMessage,
                autoRead);

        final AbstractTrafficShapingHandler handler;
        if (limitRead) {
            if (globalLimit) {
                handler = new GlobalTrafficShapingHandler(group, 0, bandwidthFactor * messageSize, check);
            } else {
                handler = new ChannelTrafficShapingHandler(0, bandwidthFactor * messageSize, check);
            }
        } else if (limitWrite) {
            if (globalLimit) {
                handler = new GlobalTrafficShapingHandler(group, bandwidthFactor * messageSize, 0, check);
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
                    if (additionalExecutor) {
                        c.pipeline().addLast(group, TRAFFIC, handler);
                    } else {
                        c.pipeline().addLast(TRAFFIC, handler);
                    }
                }
                if (additionalExecutor) {
                    c.pipeline().addLast(group, sh);
                } else {
                    c.pipeline().addLast(sh);
                }
            }
        });
        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel c) throws Exception {
                if (limitWrite) {
                    if (additionalExecutor) {
                        c.pipeline().addLast(group, TRAFFIC, handler);
                    } else {
                        c.pipeline().addLast(TRAFFIC, handler);
                    }
                }
                if (additionalExecutor) {
                    c.pipeline().addLast(group, ch);
                } else {
                    c.pipeline().addLast(ch);
                }
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        int totalNb = 0;
        for (int i = 1; i < multipleMessage.length; i++) {
            totalNb += multipleMessage[i];
        }
        Long start = System.currentTimeMillis();
        int nb = multipleMessage[0];
        for (int i = 0; i < nb; i++) {
            cc.write(cc.alloc().buffer().writeBytes(data));
        }
        cc.flush();

        promise.await();
        Long stop = System.currentTimeMillis();
        assertTrue("Error during exceution of TrafficShapping: " + promise.cause(), promise.isSuccess());

        float average = (totalNb * messageSize) / (float) (stop - start);
        logger.info("Average of traffic: " + average + " compare to " + bandwidthFactor);
        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();
        // for extra release call in AutoRead
        Thread.sleep(500);

        if (autoRead == null && minimalWaitBetween != null) {
            assertTrue("Overall Traffic not ok since > " + (bandwidthFactor + 1) + ": " + average,
                    average <= bandwidthFactor + 1);
            if (additionalExecutor) {
                // Oio is not as good when using additionalExecutor
                assertTrue("Overall Traffic not ok since < 0.25: " + average, average >= 0.25);
            } else {
                assertTrue("Overall Traffic not ok since < " + (bandwidthFactor - 1.5) + ": " + average,
                        average >= bandwidthFactor - 1.5);
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

    private static class ClientTrafficHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int step;
        // first message will always be validated
        private long currentLastTime = System.currentTimeMillis();
        private final long[] minimalWaitBetween;
        private final int[] multipleMessage;
        private final int[] autoRead;
        final Promise<Boolean> promise;

        ClientTrafficHandler(Promise<Boolean> promise, long[] minimalWaitBetween, int[] multipleMessage,
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
            while (in.isReadable()) {
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
                    } else {
                        minimalWait = 100;
                    }
                } else {
                    minimalWait = 0;
                }
            }
            loggerClient.info("Step: " + step + " Interval: " + (lastTimestamp - currentLastTime) + " compareTo "
                    + minimalWait + " (" + ar + ")");
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

    private static class ValidTimestampedHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final int[] autoRead;
        private final int[] multipleMessage;
        volatile Channel channel;
        volatile int step;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        ValidTimestampedHandler(int[] autoRead, int[] multipleMessage) {
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
            in.readBytes(actual);
            long timestamp = System.currentTimeMillis();
            int nb = actual.length / messageSize;
            int isAutoRead = 0;
            int laststep = step;
            for (int i = 0; i < nb; i++) {
                multipleMessage[step]--;
                if (multipleMessage[step] == 0) {
                    if (autoRead != null) {
                        isAutoRead = autoRead[step];
                    }
                    step++;
                }
            }
            if (laststep != step) {
                if (autoRead != null && isAutoRead != 2) {
                    if (isAutoRead != 0) {
                        loggerServer.info("Set AutoRead: " + (isAutoRead > 0) + " Step: " + step);
                        channel.config().setAutoRead(isAutoRead > 0);
                    } else {
                        loggerServer.info("AutoRead: NO Step:" + step);
                    }
                }
            }
            loggerServer.debug("Step: " + step + " Get: " + actual.length + " TS " + timestamp + " NB: " + nb);
            for (int i = 0; i < nb; i++) {
                channel.write(Unpooled.copyLong(timestamp));
            }
            channel.flush();
            if (laststep != step) {
                if (isAutoRead != 0) {
                    if (isAutoRead < 0) {
                        final int exactStep = step;
                        int wait = (isAutoRead == -1) ? 100 : 1000 / bandwidthFactor + 100;
                        if (isAutoRead == -3) {
                            wait = 1000;
                        }
                        executor.schedule(new Runnable() {
                            @Override
                            public void run() {
                                loggerServer.info("Reset AutoRead: Step " + exactStep);
                                channel.config().setAutoRead(true);
                            }
                        }, wait, TimeUnit.MILLISECONDS);
                    } else {
                        if (isAutoRead > 1) {
                            loggerServer.info("Will Set AutoRead: Rrue, Step: " + step);
                            executor.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    loggerServer.info("Set AutoRead: Rrue, Step: " + step);
                                    channel.config().setAutoRead(true);
                                }
                            }, 1000 / bandwidthFactor + 100, TimeUnit.MILLISECONDS);
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
