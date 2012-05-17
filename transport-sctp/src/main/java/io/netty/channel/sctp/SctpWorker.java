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
package io.netty.channel.sctp;

import static io.netty.channel.Channels.fireChannelBound;
import static io.netty.channel.Channels.fireChannelClosed;
import static io.netty.channel.Channels.fireChannelConnected;
import static io.netty.channel.Channels.fireChannelUnbound;
import static io.netty.channel.Channels.fireExceptionCaught;
import static io.netty.channel.Channels.fireMessageReceived;
import static io.netty.channel.Channels.fireWriteComplete;
import static io.netty.channel.Channels.succeededFuture;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MessageEvent;
import io.netty.channel.ReceiveBufferSizePredictor;
import io.netty.channel.sctp.SctpSendBufferPool.SctpSendBuffer;
import io.netty.channel.socket.nio.AbstractNioChannel;
import io.netty.channel.socket.nio.NioWorker;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;

/**
 */
public class SctpWorker extends NioWorker {

    private final SctpSendBufferPool sendBufferPool = new SctpSendBufferPool();

    public SctpWorker(Executor executor) {
        super(executor);
    }
    
    public SctpWorker(Executor executor, boolean allowShutdownOnIdle) {
        super(executor, allowShutdownOnIdle);
    }

    @Override
    public void registerWithWorker(final Channel channel, final ChannelFuture future) {
        final Selector selector = start();

        try {
            if (channel instanceof SctpServerChannelImpl) {
                final SctpServerChannelImpl ch = (SctpServerChannelImpl) channel;
                registerTaskQueue.add(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            ch.serverChannel.register(selector, SelectionKey.OP_ACCEPT, ch);
                        } catch (Throwable t) {
                            future.setFailure(t);
                            fireExceptionCaught(channel, t);
                        }
                    }
                });
                if (wakenUp.compareAndSet(false, true)) {
                    selector.wakeup();
                }
            } else if (channel instanceof SctpClientChannel) {
                final SctpClientChannel clientChannel = (SctpClientChannel) channel;
                
                registerTaskQueue.add(new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            try {
                                clientChannel.getJdkChannel().register(selector, clientChannel.getRawInterestOps() | SelectionKey.OP_CONNECT, clientChannel);
                            } catch (ClosedChannelException e) {
                                clientChannel.getWorker().close(clientChannel, succeededFuture(channel));
                            }
                            int connectTimeout = channel.getConfig().getConnectTimeoutMillis();
                            if (connectTimeout > 0) {
                                clientChannel.connectDeadlineNanos = System.nanoTime() + connectTimeout * 1000000L;
                            }
                        } catch (Throwable t) {
                            future.setFailure(t);
                            fireExceptionCaught(channel, t);
                        }
                    }
                });
                if (wakenUp.compareAndSet(false, true)) {
                    selector.wakeup();
                }
            } else {
                super.registerWithWorker(channel, future);
            }
            
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }
    
    @Override
    protected boolean accept(SelectionKey key) {
        SctpServerChannelImpl channel = (SctpServerChannelImpl) key.attachment();
        try {
            SctpChannel acceptedSocket = channel.serverChannel.accept();
            if (acceptedSocket != null) {
                
                ChannelPipeline pipeline =
                        channel.getConfig().getPipelineFactory().getPipeline();
                registerTask(new SctpAcceptedChannel(channel.getFactory(), pipeline, channel,
                        channel.getPipeline().getSink(), acceptedSocket, this), null);
                return true;
            }
            return false;
        } catch (SocketTimeoutException e) {
            // Thrown every second to get ClosedChannelException
            // raised.
        } catch (CancelledKeyException e) {
            // Raised by accept() when the server socket was closed.
        } catch (ClosedSelectorException e) {
            // Raised by accept() when the server socket was closed.
        } catch (ClosedChannelException e) {
            // Closed as requested.
        } catch (Throwable e) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to accept a connection.", e);
            }
        }
        return true;
    }


    @Override
    protected boolean read(SelectionKey k) {
        final SctpChannelImpl channel = (SctpChannelImpl) k.attachment();

        final ReceiveBufferSizePredictor predictor =
                channel.getConfig().getReceiveBufferSizePredictor();
        final int predictedRecvBufSize = predictor.nextReceiveBufferSize();

        boolean messageReceived = false;
        MessageInfo messageInfo = null;

        ByteBuffer bb = recvBufferPool.acquire(predictedRecvBufSize);
        try {
            messageInfo = channel.getJdkChannel().getChannel().receive(bb, null, channel.notificationHandler);
            messageReceived = messageInfo != null;
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (messageReceived) {
            bb.flip();

            final ChannelBufferFactory bufferFactory =
                    channel.getConfig().getBufferFactory();
            final int receivedBytes = bb.remaining();
            final ChannelBuffer buffer = bufferFactory.getBuffer(receivedBytes);
            buffer.setBytes(0, bb);
            buffer.writerIndex(receivedBytes);

            recvBufferPool.release(bb);

            // Update the predictor.
            predictor.previousReceiveBufferSize(receivedBytes);

            // Fire the event.
            fireMessageReceived(channel,
                    new SctpFrame(messageInfo, buffer),
                    messageInfo.address());
        } else {
            recvBufferPool.release(bb);
        }

        if (channel.getJdkChannel().getChannel().isBlocking() && !messageReceived) {
            k.cancel(); // Some JDK implementations run into an infinite loop without this.
            close(channel, succeededFuture(channel));
            return false;
        }

        return true;
    }

    @Override
    protected void connect(SelectionKey k) {
        final SctpClientChannel ch = (SctpClientChannel) k.attachment();
        try {
            if (ch.getJdkChannel().finishConnect()) {
                registerTask(ch, ch.connectFuture);
            }
        } catch (Throwable t) {
            ch.connectFuture.setFailure(t);
            fireExceptionCaught(ch, t);
            k.cancel(); // Some JDK implementations run into an infinite loop without this.
            ch.getWorker().close(ch, succeededFuture(ch));
        }
    }
    
    @Override
    protected void write0(AbstractNioChannel ach) {
        SctpChannelImpl channel = (SctpChannelImpl) ach;
        boolean open = true;
        boolean addOpWrite = false;
        boolean removeOpWrite = false;

        long writtenBytes = 0;

        final SctpSendBufferPool sendBufferPool = this.sendBufferPool;
        final com.sun.nio.sctp.SctpChannel ch = channel.getJdkChannel().getChannel();
        final Queue<MessageEvent> writeBuffer = channel.getWriteBufferQueue();
        final int writeSpinCount = channel.getConfig().getWriteSpinCount();
        synchronized (channel.getWriteLock()) {
            channel.setInWriteNowLoop(true);
            for (; ;) {
                MessageEvent evt = channel.getCurrentWriteEvent();
                SctpSendBuffer buf;
                if (evt == null) {
                    if ((evt = writeBuffer.poll()) == null) {
                        removeOpWrite = true;
                        channel.setWriteSuspended(false);
                        break;
                    }
                    channel.setCurrentWriteEvent(evt);

                    buf = sendBufferPool.acquire(evt.getMessage());
                    channel.setCurrentWriteBuffer(buf); 
                } else {
                    buf = channel.getCurrentWriteBuffer();
                }

                ChannelFuture future = evt.getFuture();
                try {
                    long localWrittenBytes = 0;
                    for (int i = writeSpinCount; i > 0; i--) {
                        localWrittenBytes = buf.transferTo(ch);
                        if (localWrittenBytes != 0) {
                            writtenBytes += localWrittenBytes;
                            break;
                        }
                        if (buf.finished()) {
                            break;
                        }
                    }

                    if (buf.finished()) {
                        // Successful write - proceed to the next message.
                        buf.release();
                        channel.setCurrentWriteEvent(null);
                        channel.setCurrentWriteBuffer(null);
                        evt = null;
                        buf = null;
                        future.setSuccess();
                    } else {
                        // Not written fully - perhaps the kernel buffer is full.
                        addOpWrite = true;
                        channel.setWriteSuspended(true);

                        if (localWrittenBytes > 0) {
                            // Notify progress listeners if necessary.
                            future.setProgress(
                                    localWrittenBytes,
                                    buf.writtenBytes(), buf.totalBytes());
                        }
                        break;
                    }
                } catch (AsynchronousCloseException e) {
                    // Doesn't need a user attention - ignore.
                } catch (Throwable t) {
                    buf.release();
                    channel.setCurrentWriteEvent(null);
                    channel.setCurrentWriteBuffer(null);
                    buf = null;
                    evt = null;
                    future.setFailure(t);
                    fireExceptionCaught(channel, t);
                    if (t instanceof IOException) {
                        open = false;
                        close(channel, succeededFuture(channel));
                    }
                }
            }
            channel.setInWriteNowLoop(false);
        }

        if (open) {
            if (addOpWrite) {
                setOpWrite(channel);
            } else if (removeOpWrite) {
                clearOpWrite(channel);
            }
        }

        fireWriteComplete(channel, writtenBytes);
    }

    @Override
    protected void registerTask(AbstractNioChannel ch, ChannelFuture future) {
        boolean server = !(ch instanceof SctpClientChannel);
        SctpChannelImpl channel = (SctpChannelImpl) ch;
        
        SocketAddress localAddress = channel.getLocalAddress();
        SocketAddress remoteAddress = channel.getRemoteAddress();
        if (localAddress == null || remoteAddress == null) {
            if (future != null) {
                future.setFailure(new ClosedChannelException());
            }
            close(channel, succeededFuture(channel));
            return;
        }

        try {
            if (server) {
                channel.getJdkChannel().configureBlocking(false);
            }
            
            boolean registered = channel.getJdkChannel().isRegistered();
            if (!registered) {
                synchronized (channel.getInterestedOpsLock()) {
                    channel.getJdkChannel().register(
                            selector, channel.getRawInterestOps(), channel);
                }
                
            } else {
                setInterestOps(channel, succeededFuture(channel), channel.getRawInterestOps());
            }
            if (future != null) {
                ((SctpChannelImpl) channel).setConnected();
                future.setSuccess();
            }

            if (!server) {
                if (!((SctpClientChannel) channel).boundManually) {
                    fireChannelBound(channel, localAddress);
                }
                fireChannelConnected(channel, remoteAddress);
            }
        } catch (IOException e) {
            if (future != null) {
                future.setFailure(e);
            }
            close(channel, succeededFuture(channel));
            if (!(e instanceof ClosedChannelException)) {
                throw new ChannelException(
                        "Failed to register a socket to the selector.", e);
            }
        }
    }

    @Override
    protected void processConnectTimeout(Set<SelectionKey> keys, long currentTimeNanos) {
        ConnectException cause = null;
        for (SelectionKey k: keys) {
            if (!k.isValid()) {
                // Comment the close call again as it gave us major problems with ClosedChannelExceptions.
                //
                // See:
                // * https://github.com/netty/netty/issues/142
                // * https://github.com/netty/netty/issues/138
                //
                //close(k);
                continue;
            }
            
            // Something is ready so skip it
            if (k.readyOps() != 0) {
                continue;
            }
            // check if the channel is in
            Object attachment = k.attachment();
            if (attachment instanceof SctpClientChannel) {
                SctpClientChannel ch = (SctpClientChannel) attachment;
                if (!ch.isConnected() && ch.connectDeadlineNanos > 0 && currentTimeNanos >= ch.connectDeadlineNanos) {

                    if (cause == null) {
                        cause = new ConnectException("connection timed out");
                    }

                    ch.connectFuture.setFailure(cause);
                    fireExceptionCaught(ch, cause);
                    ch.getWorker().close(ch, succeededFuture(ch));
                }
            }
            
            
            
        }
    }

    @Override
    protected void close(SelectionKey k) {
        Object attachment = k.attachment();
        if (attachment instanceof SctpServerChannelImpl) {
            SctpServerChannelImpl ch = (SctpServerChannelImpl) attachment;
            close(ch, succeededFuture(ch));
        } else {
            super.close(k);
        }
    }
    
    void close(SctpServerChannelImpl channel, ChannelFuture future) {
        boolean bound = channel.isBound();
        try {
            if (channel.serverChannel.isOpen()) {
                channel.serverChannel.close();
                if (selector != null) {
                    selector.wakeup();
                }
            }

            // Make sure the boss thread is not running so that that the future
            // is notified after a new connection cannot be accepted anymore.
            // See NETTY-256 for more information.
            channel.shutdownLock.lock();
            try {
                if (channel.setClosed()) {
                    future.setSuccess();
                    if (bound) {
                        fireChannelUnbound(channel);
                    }
                    fireChannelClosed(channel);
                } else {
                    future.setSuccess();
                }
            } finally {
                channel.shutdownLock.unlock();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }


}
