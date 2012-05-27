/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractNioChannel extends AbstractChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractNioChannel.class);

    private final SelectableChannel ch;
    private final int defaultInterestOps;
    private volatile SelectionKey selectionKey;

    /**
     * The future of the current connection attempt.  If not null, subsequent
     * connection attempts will fail.
     */
    private ChannelFuture connectFuture;
    private ScheduledFuture<?> connectTimeoutFuture;
    private ConnectException connectTimeoutException;

    protected AbstractNioChannel(Channel parent, Integer id, SelectableChannel ch, int defaultInterestOps) {
        super(parent, id);
        this.ch = ch;
        this.defaultInterestOps = defaultInterestOps;
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

    @Override
    protected NioUnsafe newUnsafe() {
        return new DefaultNioUnsafe();
    }

    protected SelectableChannel javaChannel() {
        return ch;
    }

    protected SelectionKey selectionKey() {
        assert selectionKey != null;
        return selectionKey;
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof NioChildEventLoop;
    }

    @Override
    protected boolean isFlushPending() {
        return (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
    }

    @Override
    protected void doRegister() throws Exception {
        NioChildEventLoop loop = (NioChildEventLoop) eventLoop();
        selectionKey = javaChannel().register(
                loop.selector, isActive()? defaultInterestOps : 0, this);
    }

    @Override
    protected void doDeregister() throws Exception {
        selectionKey().cancel();
        ((NioChildEventLoop) eventLoop()).cancelledKeys ++;
    }

    protected abstract boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception;

    protected abstract void doFinishConnect() throws Exception;

    protected int doReadMessages(Queue<Object> buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected int doReadBytes(ChannelBuffer buf) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected int doWriteMessages(Queue<Object> buf, boolean lastSpin) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected int doWriteBytes(ChannelBuffer buf, boolean lastSpin) throws Exception {
        throw new UnsupportedOperationException();
    }

    public interface NioUnsafe extends Unsafe {
        java.nio.channels.Channel ch();
        void finishConnect();
        void read();
    }

    private class DefaultNioUnsafe extends AbstractUnsafe implements NioUnsafe {
        @Override
        public java.nio.channels.Channel ch() {
            return javaChannel();
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelFuture future) {
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
                                    if (connectFuture == null) {
                                        return;
                                    } else {
                                        if (connectFuture.setFailure(connectTimeoutException)) {
                                            pipeline().fireExceptionCaught(connectTimeoutException);
                                            close(voidFuture());
                                        }
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
        public void read() {
            assert eventLoop().inEventLoop();

            final ChannelPipeline pipeline = pipeline();
            final ChannelBufferHolder<Object> buf = pipeline.nextIn();
            boolean closed = false;
            boolean read = false;
            try {
                if (buf.hasMessageBuffer()) {
                    Queue<Object> msgBuf = buf.messageBuffer();
                    for (;;) {
                        int localReadAmount = doReadMessages(msgBuf);
                        if (localReadAmount > 0) {
                            read = true;
                        } else if (localReadAmount == 0) {
                            break;
                        } else if (localReadAmount < 0) {
                            closed = true;
                            break;
                        }
                    }
                } else {
                    ChannelBuffer byteBuf = buf.byteBuffer();
                    for (;;) {
                        int localReadAmount = doReadBytes(byteBuf);
                        if (localReadAmount > 0) {
                            read = true;
                        } else if (localReadAmount < 0) {
                            closed = true;
                            break;
                        }
                        if (!expandReadBuffer(byteBuf)) {
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }
                pipeline().fireExceptionCaught(t);
                if (t instanceof IOException) {
                    close(voidFuture());
                }
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }
                if (closed && isOpen()) {
                    close(voidFuture());
                }
            }
        }
    }

    @Override
    protected void doFlush(ChannelBufferHolder<Object> buf) throws Exception {
        if (buf.hasByteBuffer()) {
            flushByteBuf(buf.byteBuffer());
        } else {
            flushMessageBuf(buf.messageBuffer());
        }
    }

    private void flushByteBuf(ChannelBuffer buf) throws Exception {
        if (!buf.readable()) {
            // Reset reader/writerIndex to 0 if the buffer is empty.
            buf.clear();
            return;
        }

        for (int i = config().getWriteSpinCount() - 1; i >= 0; i --) {
            int localFlushedAmount = doWriteBytes(buf, i == 0);
            if (localFlushedAmount > 0) {
                writeCounter += localFlushedAmount;
                notifyFlushFutures();
                break;
            }
            if (!buf.readable()) {
                // Reset reader/writerIndex to 0 if the buffer is empty.
                buf.clear();
                break;
            }
        }
    }

    private void flushMessageBuf(Queue<Object> buf) throws Exception {
        final int writeSpinCount = config().getWriteSpinCount() - 1;
        while (!buf.isEmpty()) {
            boolean wrote = false;
            for (int i = writeSpinCount; i >= 0; i --) {
                int localFlushedAmount = doWriteMessages(buf, i == 0);
                if (localFlushedAmount > 0) {
                    writeCounter += localFlushedAmount;
                    wrote = true;
                    notifyFlushFutures();
                    break;
                }
            }

            if (!wrote) {
                break;
            }
        }
    }

    private static boolean expandReadBuffer(ChannelBuffer byteBuf) {
        if (!byteBuf.writable()) {
            // FIXME: Magic number
            byteBuf.ensureWritableBytes(4096);
            return true;
        }

        return false;
    }
}
