/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.test.udt.bench.xfer;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.StatusUDT;
import com.barchart.udt.TypeUDT;
import com.google.caliper.Param;

import io.netty.test.udt.bench.BenchXfer;
import io.netty.test.udt.util.CaliperRunner;
import io.netty.test.udt.util.TrafficControl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.test.udt.util.UnitHelp.*;

/**
 * perform two way native UDT socket send/recv
 */
public class UdtNative extends BenchXfer {

    @Param
    private volatile int latency;

    protected static List<String> latencyValues() {
        return BenchXfer.latencyList();
    }

    @Param
    private volatile int message;

    protected static List<String> messageValues() {
        return BenchXfer.messageList();
    }

    @Param
    private volatile int duration;

    protected static List<String> durationValues() {
        return BenchXfer.durationList();
    }

    private volatile SocketUDT peer1;
    private volatile SocketUDT peer2;

    @Override
    protected void setUp() throws Exception {
        log.info("init");

        TrafficControl.delay(latency);

        final InetSocketAddress addr1 = localSocketAddress();
        final InetSocketAddress addr2 = localSocketAddress();

        peer1 = new SocketUDT(TypeUDT.DATAGRAM);
        peer2 = new SocketUDT(TypeUDT.DATAGRAM);

        peer1.setBlocking(false);
        peer2.setBlocking(false);

        peer1.setRendezvous(true);
        peer2.setRendezvous(true);

        peer1.bind(addr1);
        peer2.bind(addr2);

        socketAwait(peer1, StatusUDT.OPENED);
        socketAwait(peer2, StatusUDT.OPENED);

        peer1.connect(addr2);
        peer2.connect(addr1);

        socketAwait(peer1, StatusUDT.CONNECTED);
        socketAwait(peer2, StatusUDT.CONNECTED);

        peer1.setBlocking(true);
        peer2.setBlocking(true);

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        peer1.setBlocking(false);
        peer2.setBlocking(false);

        peer1.close();
        peer2.close();

        socketAwait(peer1, StatusUDT.CLOSED, StatusUDT.BROKEN);
        socketAwait(peer2, StatusUDT.CLOSED, StatusUDT.BROKEN);

        TrafficControl.delay(0);

        log.info("done");
    }

    /** benchmark invocation */
    public void timeMain(final int reps) throws Exception {

        final int threadCount = 4;

        final CountDownLatch completion = new CountDownLatch(threadCount);

        final AtomicBoolean isOn = new AtomicBoolean(true);

        final Runnable sendPeer1 = new Runnable() {

            @Override
            public void run() {
                try {
                    while (isOn.get()) {
                        runCore();
                    }
                } catch (final Exception e) {
                    log.error("", e);
                } finally {
                    completion.countDown();
                }
            }

            final ByteBuffer buffer = ByteBuffer.allocateDirect(message);

            long sequence;

            void runCore() throws Exception {
                buffer.rewind();
                buffer.putLong(0, sequence++);
                final int count = peer1.send(buffer);
                if (count != message) {
                    throw new Exception("count");
                }
                measure().rate().mark(count);
            }
        };

        final Runnable sendPeer2 = new Runnable() {

            @Override
            public void run() {
                try {
                    while (isOn.get()) {
                        runCore();
                    }
                } catch (final Exception e) {
                    log.error("", e);
                } finally {
                    completion.countDown();
                }
            }

            final ByteBuffer buffer = ByteBuffer.allocateDirect(message);

            long sequence;

            void runCore() throws Exception {
                buffer.rewind();
                buffer.putLong(0, sequence++);
                final int count = peer2.send(buffer);
                if (count != message) {
                    throw new Exception("count");
                }
            }
        };

        final Runnable recvPeer1 = new Runnable() {

            @Override
            public void run() {
                try {
                    while (isOn.get()) {
                        runCore();
                    }
                } catch (final Exception e) {
                    log.error("", e);
                } finally {
                    completion.countDown();
                }
            }

            final ByteBuffer buffer = ByteBuffer.allocateDirect(message);

            long sequence;

            void runCore() throws Exception {
                buffer.rewind();
                final int count = peer1.receive(buffer);
                if (count != message) {
                    throw new Exception("count");
                }
                if (sequence ++ != buffer.getLong(0)) {
                    throw new Exception("sequence");
                }
            }
        };

        final Runnable recvPeer2 = new Runnable() {

            @Override
            public void run() {
                try {
                    while (isOn.get()) {
                        runCore();
                    }
                } catch (final Exception e) {
                    log.error("", e);
                } finally {
                    completion.countDown();
                }
            }

            final ByteBuffer buffer = ByteBuffer.allocateDirect(message);

            long sequence;

            void runCore() throws Exception {
                buffer.rewind();
                final int count = peer2.receive(buffer);
                if (count != message) {
                    throw new Exception("count");
                }
                if (sequence ++ != buffer.getLong(0)) {
                    throw new Exception("sequence");
                }
            }
        };

        final ExecutorService executor = Executors
                .newFixedThreadPool(threadCount);

        executor.submit(recvPeer1);
        executor.submit(recvPeer2);
        executor.submit(sendPeer1);
        executor.submit(sendPeer2);

        markWait(duration);

        isOn.set(false);

        completion.await();

        executor.shutdownNow();
    }

    public static void main(final String[] args) throws Exception {
        CaliperRunner.execute(UdtNative.class);
    }
}
