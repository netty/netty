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

package io.netty.bench.transport.tcp;

import io.netty.bench.transport.TransferBenchmark;
import io.netty.bench.util.TrafficControl;
import io.netty.bench.util.NetworkUtil;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.caliper.Param;

/**
 * Native Socket Transfer Benchmark.
 */
public class TcpNativeTransferBenchmark extends TransferBenchmark {

    @Param
    private volatile int delayMillis;

    protected static List<String> delayMillisValues() {
        return TransferBenchmark.delayMillisValues();
    }

    @Param
    private volatile int messageSize;

    protected static List<String> messageSizeValues() {
        return TransferBenchmark.messageSizeValues();
    }

    @Param
    private volatile Mode blockingMode;

    private volatile ServerSocketChannel accept;
    private volatile SocketChannel client;
    private volatile SocketChannel server;

    private volatile ByteBuffer clientBuffer;
    private volatile ByteBuffer serverBuffer;

    private volatile Selector clientSelector;
    private volatile Selector serverSelector;

    private ExecutorService executor;

    private final Runnable taskBlocking = new Runnable() {
        @Override
        public void run() {
            final ByteBuffer buffer = serverBuffer;
            final SocketChannel channel = server;
            final int size = messageSize;
            int index = 0;
            while (true) {
                try {
                    buffer.rewind();
                    int count = 0;
                    while (buffer.hasRemaining()) {
                        count += channel.read(buffer);
                        if (count < 0) {
                            // closed
                            return;
                        }
                    }
                    if (count != size) {
                        throw new Exception("count=" + count);
                    }
                    if (index++ == buffer.getInt(0)) {
                        continue;
                    } else {
                        throw new Exception("index=" + index);
                    }
                } catch (final ClosedByInterruptException e) {
                    // closed
                    return;
                } catch (final ClosedChannelException e) {
                    // closed
                    return;
                } catch (final Exception e) {
                    // failed
                    e.printStackTrace();
                    return;
                }
            }
        }
    };

    private final Runnable taskNonBlocking = new Runnable() {
        @Override
        public void run() {
            final ByteBuffer buffer = serverBuffer;
            final SocketChannel channel = server;
            final int size = messageSize;
            final Selector selector = serverSelector;
            final Set<SelectionKey> selectedKeys = selector.selectedKeys();
            int index = 0;
            while (true) {
                try {
                    buffer.rewind();
                    int count = 0;
                    while (buffer.hasRemaining()) {
                        while (selector.select(1000) == 0
                                && !selectedKeys.isEmpty()) {
                            NetworkUtil.log("server wait");
                        }
                        count += channel.read(buffer);
                        selectedKeys.clear();
                    }
                    if (count != size) {
                        throw new Exception("count=" + count);
                    }
                    if (index++ == buffer.getInt(0)) {
                        continue;
                    } else {
                        throw new Exception("index=" + index);
                    }
                } catch (final ClosedByInterruptException e) {
                    // closed
                    return;
                } catch (final ClosedChannelException e) {
                    // closed
                    return;
                } catch (final ClosedSelectorException e) {
                    // closed
                    return;
                } catch (final Exception e) {
                    // failed
                    e.printStackTrace();
                    return;
                }
            }
        }
    };

    @Override
    protected void setUp() throws Exception {
        TrafficControl.delay(delayMillis);

        accept = ServerSocketChannel.open();
        accept.configureBlocking(true);
        accept.socket().bind(NetworkUtil.localSocketAddress());

        client = SocketChannel.open();
        client.configureBlocking(false);
        client.socket().bind(NetworkUtil.localSocketAddress());
        client.connect(accept.socket().getLocalSocketAddress());

        server = accept.accept();
        server.configureBlocking(false);

        client.finishConnect();

        NetworkUtil.socketAwait(client);
        NetworkUtil.socketAwait(server);

        clientBuffer = ByteBuffer.allocateDirect(messageSize);
        serverBuffer = ByteBuffer.allocateDirect(messageSize);

        executor = Executors.newFixedThreadPool(1);

        clientSelector = Selector.open();
        serverSelector = Selector.open();

        switch (blockingMode) {
        case BLOCKING:
            client.configureBlocking(true);
            server.configureBlocking(true);
            executor.submit(taskBlocking);
            break;
        case NON_BLOCKING:
            client.configureBlocking(false);
            client.register(clientSelector, SelectionKey.OP_WRITE);
            server.configureBlocking(false);
            server.register(serverSelector, SelectionKey.OP_READ);
            executor.submit(taskNonBlocking);
            break;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        executor.shutdownNow();

        client.close();
        server.close();
        accept.close();

        clientSelector.close();
        serverSelector.close();

        TrafficControl.delay(0);
    }

    public void timeXfer(final int reps) throws Exception {
        final ByteBuffer buffer = clientBuffer;
        final int size = messageSize;
        final Mode mode = blockingMode;
        final SocketChannel channel = client;
        final Selector selector = clientSelector;
        final Set<SelectionKey> selectedKeys = selector.selectedKeys();
        for (int index = 0; index < reps; index++) {
            buffer.rewind();
            buffer.putInt(0, index);
            int count = 0;
            switch (mode) {
            case BLOCKING:
                while (buffer.hasRemaining()) {
                    count += channel.write(buffer);
                }
                break;
            case NON_BLOCKING:
                while (buffer.hasRemaining()) {
                    while (selector.select(1000) == 0
                            && !selectedKeys.isEmpty()) {
                        NetworkUtil.log("client wait");
                    }
                    count += channel.write(buffer);
                    selectedKeys.clear();
                }
                break;
            }
            if (count != size) {
                throw new Exception("count=" + count);
            }
        }
    }

    public static void main(final String... args) throws Exception {
        new TcpNativeTransferBenchmark().execute();
    }

}
