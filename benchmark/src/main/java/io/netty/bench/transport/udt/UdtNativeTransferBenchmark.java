/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.bench.transport.udt;

import io.netty.bench.transport.TransferBenchmark;
import io.netty.bench.util.TrafficControl;
import io.netty.bench.util.NetworkUtil;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.barchart.udt.SocketUDT;
import com.barchart.udt.StatusUDT;
import com.barchart.udt.TypeUDT;
import com.google.caliper.Param;

/**
 * Native Socket Transfer Benchmark.
 */
public class UdtNativeTransferBenchmark extends TransferBenchmark {

    @Param
    private int delayMillis;

    protected static List<String> delayMillisValues() {
        return TransferBenchmark.delayMillisValues();
    }

    @Param
    private int messageSize;

    protected static List<String> messageSizeValues() {
        return TransferBenchmark.messageSizeValues();
    }

    @Param
    private Mode blockingMode;

    private SocketUDT peer1;
    private SocketUDT peer2;

    private ByteBuffer buf1;
    private ByteBuffer buf2;

    private ExecutorService executor;
    private Runnable task;

    @Override
    protected void setUp() throws Exception {
        TrafficControl.delay(delayMillis);

        final InetSocketAddress addr1 = NetworkUtil.localSocketAddress();
        final InetSocketAddress addr2 = NetworkUtil.localSocketAddress();

        peer1 = new SocketUDT(TypeUDT.DATAGRAM);
        peer2 = new SocketUDT(TypeUDT.DATAGRAM);

        peer1.setBlocking(false);
        peer2.setBlocking(false);

        peer1.setRendezvous(true);
        peer2.setRendezvous(true);

        peer1.bind(addr1);
        peer2.bind(addr2);

        NetworkUtil.socketAwait(peer1, StatusUDT.OPENED);
        NetworkUtil.socketAwait(peer2, StatusUDT.OPENED);

        peer1.connect(addr2);
        peer2.connect(addr1);

        NetworkUtil.socketAwait(peer1, StatusUDT.CONNECTED);
        NetworkUtil.socketAwait(peer2, StatusUDT.CONNECTED);

        switch (blockingMode) {
        case BLOCKING:
            peer1.setBlocking(true);
            peer2.setBlocking(true);
            break;
        case NON_BLOCKING:
            peer1.setBlocking(false);
            peer2.setBlocking(false);
            break;
        }

        buf1 = ByteBuffer.allocateDirect(messageSize);
        buf2 = ByteBuffer.allocateDirect(messageSize);

        executor = Executors.newFixedThreadPool(1);

        task = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        buf2.rewind();
                        if (peer2.receive(buf2) > 0) {
                            continue;
                        } else {
                            try {
                                Thread.sleep(10);
                            } catch (final InterruptedException e) {
                                return;
                            }
                        }
                    } catch (final Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        };

        executor.submit(task);
    }

    @Override
    protected void tearDown() throws Exception {
        executor.shutdownNow();
        Thread.sleep(100);
        peer1.close();
        peer2.close();
        TrafficControl.delay(0);
    }

    public void timeXfer(final int reps) throws Exception {
        for (int i = 0; i < reps; i++) {
            buf1.rewind();
            peer1.send(buf1);
        }
    }

    public static void main(final String... args) throws Exception {
        new UdtNativeTransferBenchmark().execute();
    }

}
