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
package io.netty.channel.socket.nio;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    private final int readInterestOp;
    private volatile SelectionKey selectionKey;
    private volatile boolean inputShutdown;
    final Queue<NioTask<SelectableChannel>> writableTasks = new ConcurrentLinkedQueue<NioTask<SelectableChannel>>();

    final Runnable suspendReadTask = new Runnable() {
        @Override
        public void run() {
            selectionKey().interestOps(selectionKey().interestOps() & ~readInterestOp);
        }

    };

    final Runnable resumeReadTask = new Runnable() {
        @Override
        public void run() {
            selectionKey().interestOps(selectionKey().interestOps() | readInterestOp);
        }
    };

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelFuture connectFuture;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    protected AbstractNioChannel(
            Channel parent, Integer id, SelectableChannel ch, int readInterestOp) {
        super(parent, id);
        this.ch = ch;
        this.readInterestOp = readInterestOp;
        try {
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized socket.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public NioUnsafe unsafe() {
        return (NioUnsafe) super.unsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    boolean isInputShutdown() {
        return inputShutdown;
    }

    void setInputShutdown() {
        inputShutdown = true;
    }

    public interface NioUnsafe extends Unsafe {
        SelectableChannel ch();
        void finishConnect();
        void read();
    }

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe implements NioUnsafe {

        @Override
        public SelectableChannel ch() {
            return javaChannel();
        }

        @Override
        public void connect(
                final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
            if (eventLoop().inEventLoop()) {
                if (!ensureOpen(future)) {
                    return;
                }

                try {
                    if (connectFuture != null) {
                        throw new IllegalStateException("connection attempt already made");
                    }

                    boolean wasActive = isActive();
                    if (doConnect(remoteAddress, localAddress)) {
                        future.setSuccess();
                        if (!wasActive && isActive()) {
                            pipeline().fireChannelActive();
                        }
                    } else {
                        connectFuture = future;

                        // Schedule connect timeout.
                        int connectTimeoutMillis = config().getConnectTimeoutMillis();
                        if (connectTimeoutMillis > 0) {
                            connectTimeoutFuture = eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (connectTimeoutException == null) {
                                        connectTimeoutException = new ConnectException("connection timed out");
                                    }
                                    ChannelFuture connectFuture = AbstractNioChannel.this.connectFuture;
                                    if (connectFuture != null && connectFuture.setFailure(connectTimeoutException)) {
                                        pipeline().fireExceptionCaught(connectTimeoutException);
                                        close(voidFuture());
                                    }
                                }
                            }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
                        }
                    }
                } catch (Throwable t) {
                    future.setFailure(t);
                    pipeline().fireExceptionCaught(t);
                    closeIfClosed();
                }
            } else {
                eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        connect(remoteAddress, localAddress, future);
                    }
                });
            }
        }

        @Override
        public void finishConnect() {
            assert eventLoop().inEventLoop();
            assert connectFuture != null;
            try {
                boolean wasActive = isActive();
                doFinishConnect();
                connectFuture.setSuccess();
                if (!wasActive && isActive()) {
                    pipeline().fireChannelActive();
                }
            } catch (Throwable t) {
                connectFuture.setFailure(t);
                pipeline().fireExceptionCaught(t);
                closeIfClosed();
            } finally {
                connectTimeoutFuture.cancel(false);
                connectFuture = null;
            }
        }

        @Override
        public void suspendRead() {
            EventLoop loop = eventLoop();
            if (loop.inEventLoop()) {
                suspendReadTask.run();
            } else {
                loop.execute(suspendReadTask);
            }
        }

        @Override
        public void resumeRead() {
            if (inputShutdown) {
                return;
            }

            EventLoop loop = eventLoop();
            if (loop.inEventLoop()) {
                resumeReadTask.run();
            } else {
                loop.execute(resumeReadTask);
            }
        }
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioEventLoop;
    }

    @Override
    protected boolean isFlushPending() {
        SelectionKey selectionKey = this.selectionKey;
        return selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
    }

    @Override
    protected Runnable doRegister() throws Exception {
        NioEventLoop loop = (NioEventLoop) eventLoop();
        selectionKey = javaChannel().register(
                loop.selector, isActive() && !inputShutdown ? readInterestOp : 0, this);
        return null;
    }

    @Override
    protected void doDeregister() throws Exception {
        ((NioEventLoop) eventLoop()).cancel(selectionKey());
    }

    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;
    protected abstract void doFinishConnect() throws Exception;
}
