/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.nio;

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.LinkedTransferQueue;
import org.jboss.netty.util.ThreadRenamingRunnable;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class NioClientSocketPipelineSink extends AbstractChannelSink {

    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioClientSocketPipelineSink.class);
    private static final AtomicInteger nextId = new AtomicInteger();

    final int id = nextId.incrementAndGet();
    final Executor bossExecutor;
    private final Boss boss = new Boss();
    private final NioWorker[] workers;
    private final AtomicInteger workerIndex = new AtomicInteger();

    NioClientSocketPipelineSink(
            Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this.bossExecutor = bossExecutor;
        workers = new NioWorker[workerCount];
        for (int i = 0; i < workers.length; i ++) {
            workers[i] = new NioWorker(id, i + 1, workerExecutor);
        }
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            NioClientSocketChannel channel =
                (NioClientSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    NioWorker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    NioWorker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    NioWorker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                NioWorker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            channel.writeBuffer.offer(event);
            NioWorker.write(channel, true);
        }
    }

    private void bind(
            NioClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.socket.socket().bind(localAddress);
            channel.boundManually = true;
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(
            final NioClientSocketChannel channel, ChannelFuture future,
            SocketAddress remoteAddress) {
        try {
            if (channel.socket.connect(remoteAddress)) {
                NioWorker worker = nextWorker();
                channel.setWorker(worker);
                worker.register(channel, future);
            } else {
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) {
                        if (future.isCancelled()) {
                            channel.close();
                        }
                    }
                });
                channel.connectFuture = future;
                boss.register(channel);
            }

        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    NioWorker nextWorker() {
        return workers[Math.abs(
                workerIndex.getAndIncrement() % workers.length)];
    }

    private final class Boss implements Runnable {

        volatile Selector selector;
        private boolean started;
        private final AtomicBoolean wakenUp = new AtomicBoolean();
        private final Object startStopLock = new Object();
        private final Queue<Runnable> registerTaskQueue = new LinkedTransferQueue<Runnable>();

        Boss() {
            super();
        }

        void register(NioSocketChannel channel) {
            Runnable registerTask = new RegisterTask(this, channel);
            Selector selector;

            synchronized (startStopLock) {
                if (!started) {
                    // Open a selector if this worker didn't start yet.
                    try {
                        this.selector = selector =  Selector.open();
                    } catch (Throwable t) {
                        throw new ChannelException(
                                "Failed to create a selector.", t);
                    }

                    // Start the worker thread with the new Selector.
                    boolean success = false;
                    try {
                        bossExecutor.execute(new ThreadRenamingRunnable(
                                this, "New I/O client boss #" + id));
                        success = true;
                    } finally {
                        if (!success) {
                            // Release the Selector if the execution fails.
                            try {
                                selector.close();
                            } catch (Throwable t) {
                                logger.warn("Failed to close a selector.", t);
                            }
                            this.selector = selector = null;
                            // The method will return to the caller at this point.
                        }
                    }
                } else {
                    // Use the existing selector if this worker has been started.
                    selector = this.selector;
                }

                assert selector != null && selector.isOpen();

                started = true;
                registerTaskQueue.offer(registerTask);
            }

            if (wakenUp.compareAndSet(false, true)) {
                selector.wakeup();
            }
        }

        public void run() {
            boolean shutdown = false;
            Selector selector = this.selector;
            for (;;) {
                wakenUp.set(false);

                try {
                    int selectedKeyCount = selector.select(500);

                    processRegisterTaskQueue();

                    if (selectedKeyCount > 0) {
                        processSelectedKeys(selector.selectedKeys());
                    }

                    // Exit the loop when there's nothing to handle.
                    // The shutdown flag is used to delay the shutdown of this
                    // loop to avoid excessive Selector creation when
                    // connection attempts are made in a one-by-one manner
                    // instead of concurrent manner.
                    if (selector.keys().isEmpty()) {
                        if (shutdown ||
                            bossExecutor instanceof ExecutorService && ((ExecutorService) bossExecutor).isShutdown()) {

                            synchronized (startStopLock) {
                                if (registerTaskQueue.isEmpty() && selector.keys().isEmpty()) {
                                    started = false;
                                    try {
                                        selector.close();
                                    } catch (IOException e) {
                                        logger.warn(
                                                "Failed to close a selector.", e);
                                    } finally {
                                        this.selector = null;
                                    }
                                    break;
                                } else {
                                    shutdown = false;
                                }
                            }
                        } else {
                            // Give one more second.
                            shutdown = true;
                        }
                    } else {
                        shutdown = false;
                    }
                } catch (Throwable t) {
                    logger.warn(
                            "Unexpected exception in the selector loop.", t);

                    // Prevent possible consecutive immediate failures.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
            }
        }

        private void processRegisterTaskQueue() {
            for (;;) {
                final Runnable task = registerTaskQueue.poll();
                if (task == null) {
                    break;
                }

                task.run();
            }
        }

        private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
            for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
                SelectionKey k = i.next();
                i.remove();

                if (!k.isValid()) {
                    close(k);
                    continue;
                }

                if (k.isConnectable()) {
                    connect(k);
                }
            }
        }

        private void connect(SelectionKey k) {
            NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
            try {
                if (ch.socket.finishConnect()) {
                    k.cancel();
                    NioWorker worker = nextWorker();
                    ch.setWorker(worker);
                    worker.register(ch, ch.connectFuture);
                }
            } catch (Throwable t) {
                ch.connectFuture.setFailure(t);
                fireExceptionCaught(ch, t);
                close(k);
            }
        }

        private void close(SelectionKey k) {
            k.cancel();
            NioSocketChannel ch = (NioSocketChannel) k.attachment();
            NioWorker.close(ch, ch.getSucceededFuture());
        }
    }

    private static final class RegisterTask implements Runnable {
        private final Boss boss;
        private final NioSocketChannel channel;

        RegisterTask(Boss boss, NioSocketChannel channel) {
            this.boss = boss;
            this.channel = channel;
        }

        public void run() {
            try {
                channel.socket.register(
                        boss.selector, SelectionKey.OP_CONNECT, channel);
            } catch (ClosedChannelException e) {
                throw new ChannelException(
                        "Failed to register a socket to the selector.", e);
            }
        }
    }
}
