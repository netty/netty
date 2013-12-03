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
package org.jboss.netty.channel.socket.oio;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.Worker;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.jboss.netty.channel.Channels.*;

/**
 * Abstract base class for Oio-Worker implementations
 *
 * @param <C> {@link AbstractOioChannel}
 */
abstract class AbstractOioWorker<C extends AbstractOioChannel> implements Worker {

    private final Queue<Runnable> eventQueue = new ConcurrentLinkedQueue<Runnable>();

    protected final C channel;

    /**
     * If this worker has been started thread will be a reference to the thread
     * used when starting. i.e. the current thread when the run method is executed.
     */
    protected volatile Thread thread;

    private volatile boolean done;

    protected AbstractOioWorker(C channel) {
        this.channel = channel;
        channel.worker = this;
    }

    public void run() {
        thread = channel.workerThread = Thread.currentThread();
        while (channel.isOpen()) {
            synchronized (channel.interestOpsLock) {
                while (!channel.isReadable()) {
                    try {
                        // notify() is not called at all.
                        // close() and setInterestOps() calls Thread.interrupt()
                        channel.interestOpsLock.wait();
                    } catch (InterruptedException e) {
                        if (!channel.isOpen()) {
                            break;
                        }
                    }
                }
            }

            boolean cont = false;
            try {
                cont = process();
            } catch (Throwable t) {
                boolean readTimeout = t instanceof SocketTimeoutException;
                if (!readTimeout && !channel.isSocketClosed()) {
                    fireExceptionCaught(channel, t);
                }
                if (readTimeout) {
                    // the readTimeout was triggered because of the SO_TIMEOUT,
                    // so  just continue with the loop here
                    cont = true;
                }
            } finally {
                processEventQueue();
            }

            if (!cont) {
                break;
            }
        }

        synchronized (channel.interestOpsLock) {
            // Setting the workerThread to null will prevent any channel
            // operations from interrupting this thread from now on.
            //
            //
            // Do this while holding the lock to not race with close(...) or
            // setInterestOps(...)
            channel.workerThread = null;
        }

        // Clean up.
        close(channel, succeededFuture(channel), true);

        // Mark the worker event loop as done so we know that we need to run tasks directly and not queue them
        // See #287
        done = true;

        // just to make we don't have something left
        processEventQueue();
    }

    static boolean isIoThread(AbstractOioChannel channel) {
        return Thread.currentThread() == channel.workerThread;
    }

    public void executeInIoThread(Runnable task) {
        // check if the current thread is the worker thread
        //
        // Also check if the event loop of the worker is complete. If so we need to run the task now.
        // See #287
        if (Thread.currentThread() == thread || done) {
            task.run();
        } else {
            boolean added = eventQueue.offer(task);

            if (added) {
                // as we set the SO_TIMEOUT to 1 second this task will get picked up in 1 second at latest
            }
        }
    }

    private void processEventQueue() {
        for (;;) {
            final Runnable task = eventQueue.poll();
            if (task == null) {
                break;
            }
            task.run();
        }
    }

    /**
     * Process the incoming messages and also is responsible for call
     * {@link Channels#fireMessageReceived(Channel, Object)} once a message was processed without
     * errors.
     *
     * @return continue returns {@code true} as long as this worker should continue to try
     *         processing incoming messages
     * @throws IOException
     */
    abstract boolean process() throws IOException;

    static void setInterestOps(
            AbstractOioChannel channel, ChannelFuture future, int interestOps) {
        boolean iothread = isIoThread(channel);

        // Override OP_WRITE flag - a user cannot change this flag.
        interestOps &= ~Channel.OP_WRITE;
        interestOps |= channel.getInterestOps() & Channel.OP_WRITE;

        boolean changed = false;
        try {
            if (channel.getInterestOps() != interestOps) {
                if ((interestOps & Channel.OP_READ) != 0) {
                    channel.setInterestOpsNow(Channel.OP_READ);
                } else {
                    channel.setInterestOpsNow(Channel.OP_NONE);
                }
                changed = true;
            }

            future.setSuccess();
            if (changed) {
                synchronized (channel.interestOpsLock) {
                    channel.setInterestOpsNow(interestOps);

                    // Notify the worker so it stops or continues reading.
                    Thread currentThread = Thread.currentThread();
                    Thread workerThread = channel.workerThread;
                    if (workerThread != null && currentThread != workerThread) {
                        workerThread.interrupt();
                    }
                }
                if (iothread) {
                    fireChannelInterestChanged(channel);
                } else {
                    fireChannelInterestChangedLater(channel);
                }
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (iothread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
            }
        }
    }

    static void close(AbstractOioChannel channel, ChannelFuture future) {
        close(channel, future, isIoThread(channel));
    }

    private static void close(AbstractOioChannel channel, ChannelFuture future, boolean iothread) {
        boolean connected = channel.isConnected();
        boolean bound = channel.isBound();

        try {
            channel.closeSocket();
            if (channel.setClosed()) {
                future.setSuccess();
                if (connected) {
                    Thread currentThread = Thread.currentThread();
                    synchronized (channel.interestOpsLock) {
                        // We need to do this while hold the lock as otherwise
                        // we may race and so interrupt the workerThread even
                        // if we are in the workerThread now.
                        // This can happen if run() set channel.workerThread to null
                        // between workerThread != null and currentThread!= workerThread
                        Thread workerThread = channel.workerThread;
                        if (workerThread != null && currentThread != workerThread) {
                            workerThread.interrupt();
                        }
                    }

                    if (iothread) {
                        fireChannelDisconnected(channel);
                    } else {
                        fireChannelDisconnectedLater(channel);
                    }
                }
                if (bound) {
                    if (iothread) {
                        fireChannelUnbound(channel);
                    } else {
                        fireChannelUnboundLater(channel);
                    }
                }
                if (iothread) {
                    fireChannelClosed(channel);
                } else {
                    fireChannelClosedLater(channel);
                }
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (iothread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
            }
        }
    }
}
