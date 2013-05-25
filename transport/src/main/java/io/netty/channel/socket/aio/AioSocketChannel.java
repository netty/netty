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
package io.netty.channel.socket.aio;

import io.netty.buffer.BufType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFlushPromiseNotifier;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.aio.AbstractAioChannel;
import io.netty.channel.aio.AioCompletionHandler;
import io.netty.channel.aio.AioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;


/**
 * {@link SocketChannel} implementation which uses NIO2.
 *
 * NIO2 is only supported on Java 7+.
 */
public class AioSocketChannel extends AbstractAioChannel implements SocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(BufType.BYTE, false);

    private static final CompletionHandler<Void, AioSocketChannel> CONNECT_HANDLER  = new ConnectHandler();
    private static final CompletionHandler<Integer, AioSocketChannel> WRITE_HANDLER = new WriteHandler<Integer>();
    private static final CompletionHandler<Integer, AioSocketChannel> READ_HANDLER = new ReadHandler<Integer>();
    private static final CompletionHandler<Long, AioSocketChannel> GATHERING_WRITE_HANDLER = new WriteHandler<Long>();
    private static final CompletionHandler<Long, AioSocketChannel> SCATTERING_READ_HANDLER = new ReadHandler<Long>();

    private static AsynchronousSocketChannel newSocket(AsynchronousChannelGroup group) {
        try {
            return AsynchronousSocketChannel.open(group);
        } catch (IOException e) {
            throw new ChannelException("Failed to open a socket.", e);
        }
    }

    private final DefaultAioSocketChannelConfig config;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    private boolean readInProgress;
    private boolean inDoBeginRead;
    private boolean readAgain;

    private static final int NO_WRITE_IN_PROGRESS = 0;
    private static final int WRITE_IN_PROGRESS = 1;
    private static final int WRITE_FAILED = -2;

    private int writeInProgress;
    private boolean inDoFlushByteBuffer;

    /**
     * Create a new instance which has not yet attached an {@link AsynchronousSocketChannel}. The
     * {@link AsynchronousSocketChannel} will be attached after it was this instance was registered to an
     * {@link EventLoop}.
     */
    public AioSocketChannel() {
        this(null, null, null);
    }

    /**
     * Create a new instance from the given {@link AsynchronousSocketChannel}.
     *
     * @param parent
     *        the parent of this channel. {@code null} if there's no parent.
     * @param id
     *        the unique non-negative integer ID of this channel.
     *        Specify {@code null} to auto-generate a unique negative integer
     *        ID.
     * @param ch
     *        the {@link AsynchronousSocketChannel} which is used by this instance
     */
    AioSocketChannel(
            AioServerSocketChannel parent, Integer id, AsynchronousSocketChannel ch) {
        super(parent, id, ch);
        config = new DefaultAioSocketChannelConfig(this, ch);
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
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    public boolean isActive() {
        return ch != null && javaChannel().isOpen() && remoteAddress0() != null;
    }

    @Override
    protected AsynchronousSocketChannel javaChannel() {
        return (AsynchronousSocketChannel) super.javaChannel();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        EventLoop loop = eventLoop();
        if (loop.inEventLoop()) {
            try {
                javaChannel().shutdownOutput();
                outputShutdown = true;
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            loop.execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput(promise);
                }
            });
        }
        return promise;
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, final ChannelPromise promise) {
        if (localAddress != null) {
            try {
                javaChannel().bind(localAddress);
            } catch (IOException e) {
                promise.setFailure(e);
                return;
            }
        }

        javaChannel().connect(remoteAddress, this, CONNECT_HANDLER);
    }

    @Override
    protected InetSocketAddress localAddress0() {
        if (ch == null) {
            return null;
        }
        try {
            return (InetSocketAddress) javaChannel().getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        if (ch == null) {
            return null;
        }
        try {
            return (InetSocketAddress) javaChannel().getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Runnable doRegister() throws Exception {
        super.doRegister();
        if (ch == null) {
            ch = newSocket(((AioEventLoopGroup) eventLoop().parent()).channelGroup());
            config.assign(javaChannel());
        }

        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
        inputShutdown = true;
        outputShutdown = true;
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        if (inDoFlushByteBuffer || writeInProgress != NO_WRITE_IN_PROGRESS) {
            return;
        }

        inDoFlushByteBuffer = true;

        try {
            if (buf.isReadable()) {
                for (;;) {
                    if (buf.refCnt() == 0) {
                        break;
                    }
                    // Ensure the readerIndex of the buffer is 0 before beginning an async write.
                    // Otherwise, JDK can write into a wrong region of the buffer when a handler calls
                    // discardReadBytes() later, modifying the readerIndex and the writerIndex unexpectedly.
                    buf.discardReadBytes();

                    writeInProgress = WRITE_IN_PROGRESS;
                    if (buf.nioBufferCount() == 1) {
                        javaChannel().write(
                                buf.nioBuffer(), config.getWriteTimeout(), TimeUnit.MILLISECONDS, this, WRITE_HANDLER);
                    } else {
                        ByteBuffer[] buffers = buf.nioBuffers(buf.readerIndex(), buf.readableBytes());
                        if (buffers.length == 1) {
                            javaChannel().write(
                                    buffers[0], config.getWriteTimeout(), TimeUnit.MILLISECONDS, this, WRITE_HANDLER);
                        } else {
                            javaChannel().write(
                                    buffers, 0, buffers.length, config.getWriteTimeout(), TimeUnit.MILLISECONDS,
                                    this, GATHERING_WRITE_HANDLER);
                        }
                    }

                    if (writeInProgress != NO_WRITE_IN_PROGRESS) {
                        if (writeInProgress == WRITE_FAILED) {
                            // failed because of an exception so reset state and break out of the loop now
                            // See #1242
                            writeInProgress = NO_WRITE_IN_PROGRESS;
                            break;
                        }
                        // JDK decided to write data (or notify handler) later.
                        buf.suspendIntermediaryDeallocations();
                        break;
                    }

                    // JDK performed the write operation immediately and notified the handler.
                    // We know this because we set asyncWriteInProgress to false in the handler.
                    if (!buf.isReadable()) {
                        // There's nothing left in the buffer. No need to retry writing.
                        break;
                    }

                    // There's more to write. Continue the loop.
                }
            } else {
                flushFutureNotifier.notifyFlushFutures();
            }
        } finally {
            inDoFlushByteBuffer = false;
        }
    }

    @Override
    protected void doFlushFileRegion(FlushTask task) throws Exception {
        task.region().transferTo(new WritableByteChannelAdapter(task), 0);
    }

    @Override
    protected void doBeginRead() {
        if (inDoBeginRead) {
            readAgain = true;
            return;
        }

        if (readInProgress || inputShutdown) {
            return;
        }

        inDoBeginRead = true;
        try {
            for (;;) {
                if (inputShutdown) {
                    break;
                }

                ByteBuf byteBuf = pipeline().inboundByteBuffer();

                // Ensure the readerIndex of the buffer is 0 before beginning an async read.
                // Otherwise, JDK can read into a wrong region of the buffer when a handler calls
                // discardReadBytes() later, modifying the readerIndex and the writerIndex unexpectedly.
                // See https://github.com/netty/netty/issues/1377
                byteBuf.discardReadBytes();

                expandReadBuffer(byteBuf);

                readInProgress = true;
                if (byteBuf.nioBufferCount() == 1) {
                    // Get a ByteBuffer view on the ByteBuf
                    ByteBuffer buffer = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
                    javaChannel().read(
                            buffer, config.getReadTimeout(), TimeUnit.MILLISECONDS, this, READ_HANDLER);
                } else {
                    ByteBuffer[] buffers = byteBuf.nioBuffers(byteBuf.writerIndex(), byteBuf.writableBytes());
                    if (buffers.length == 1) {
                        javaChannel().read(
                                buffers[0], config.getReadTimeout(), TimeUnit.MILLISECONDS, this, READ_HANDLER);
                    } else {
                        javaChannel().read(
                                buffers, 0, buffers.length, config.getReadTimeout(), TimeUnit.MILLISECONDS,
                                this, SCATTERING_READ_HANDLER);
                    }
                }

                if (readInProgress) {
                    // JDK decided to read data (or notify handler) later.
                    break;
                }

                if (readAgain) {
                    // User requested the read operation.
                    readAgain = false;
                    continue;
                }

                break;
            }
        } finally {
            inDoBeginRead = false;
        }
    }

    private static final class WriteHandler<T extends Number> extends AioCompletionHandler<T, AioSocketChannel> {

        @Override
        protected void completed0(T result, AioSocketChannel channel) {
            channel.writeInProgress = NO_WRITE_IN_PROGRESS;

            ByteBuf buf = channel.unsafe().headContext().outboundByteBuffer();
            if (buf.refCnt() == 0) {
                return;
            }

            buf.resumeIntermediaryDeallocations();

            int writtenBytes = result.intValue();
            if (writtenBytes > 0) {
                // Update the readerIndex with the amount of read bytes
                buf.readerIndex(buf.readerIndex() + writtenBytes);
            }

            if (channel.inDoFlushByteBuffer) {
                // JDK performed the write operation immediately and notified this handler immediately.
                // doFlushByteBuffer() will do subsequent write operations if necessary for us.
                return;
            }

            // Update the write counter and notify flush futures only when the handler is called outside of
            // unsafe().flushNow() because flushNow() will do that for us.
            ChannelFlushPromiseNotifier notifier = channel.flushFutureNotifier;
            notifier.increaseWriteCounter(writtenBytes);
            notifier.notifyFlushFutures();

            // Stop flushing if disconnected.
            if (!channel.isActive()) {
                return;
            }

            if (buf.isReadable()) {
                channel.unsafe().flushNow();
            }
        }

        @Override
        protected void failed0(Throwable cause, AioSocketChannel channel) {
            channel.writeInProgress = WRITE_FAILED;
            channel.flushFutureNotifier.notifyFlushFutures(cause);

            // Check if the exception was raised because of an InterruptedByTimeoutException which means that the
            // write timeout was hit. In that case we should close the channel as it may be unusable anyway.
            //
            // See http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousSocketChannel.html
            if (cause instanceof InterruptedByTimeoutException) {
                channel.unsafe().close(channel.unsafe().voidPromise());
            }
        }
    }

    private static final class ReadHandler<T extends Number> extends AioCompletionHandler<T, AioSocketChannel> {

        @Override
        protected void completed0(T result, AioSocketChannel channel) {
            channel.readInProgress = false;

            if (channel.inputShutdown) {
                // Channel has been closed during read. Because the inbound buffer has been deallocated already,
                // there's no way to let a user handler access it unfortunately.
                return;
            }

            final ChannelPipeline pipeline = channel.pipeline();
            final ByteBuf byteBuf = pipeline.inboundByteBuffer();

            boolean closed = false;
            boolean read = false;
            boolean firedChannelReadSuspended = false;
            try {
                int localReadAmount = result.intValue();
                if (localReadAmount > 0) {
                    // Set the writerIndex of the buffer correctly to the
                    // current writerIndex + read amount of bytes.
                    //
                    // This is needed as the ByteBuffer and the ByteBuf does not share
                    // each others index
                    byteBuf.writerIndex(byteBuf.writerIndex() + localReadAmount);

                    read = true;
                } else if (localReadAmount < 0) {
                    closed = true;
                }
            } catch (Throwable t) {
                if (read) {
                    read = false;
                    pipeline.fireInboundBufferUpdated();
                }

                if (!closed && channel.isOpen()) {
                    firedChannelReadSuspended = true;
                    pipeline.fireChannelReadSuspended();
                }

                pipeline.fireExceptionCaught(t);
            } finally {
                if (read) {
                    pipeline.fireInboundBufferUpdated();
                }

                // Double check because fireInboundBufferUpdated() might have triggered the closure by a user handler.
                if (closed || !channel.isOpen()) {
                    channel.inputShutdown = true;
                    if (channel.isOpen()) {
                        if (channel.config().isAllowHalfClosure()) {
                            pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                        } else {
                            channel.unsafe().close(channel.unsafe().voidPromise());
                        }
                    }
                } else if (!firedChannelReadSuspended) {
                    pipeline.fireChannelReadSuspended();
                }
            }
        }

        @Override
        protected void failed0(Throwable t, AioSocketChannel channel) {
            channel.readInProgress = false;
            if (t instanceof ClosedChannelException) {
                channel.inputShutdown = true;
                return;
            }

            channel.pipeline().fireExceptionCaught(t);

            // Check if the exception was raised because of an InterruptedByTimeoutException which means that the
            // write timeout was hit. In that case we should close the channel as it may be unusable anyway.
            //
            // See http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousSocketChannel.html
            if (t instanceof IOException || t instanceof InterruptedByTimeoutException) {
                channel.unsafe().close(channel.unsafe().voidPromise());
            }
        }
    }

    private static final class ConnectHandler extends AioCompletionHandler<Void, AioSocketChannel> {

        @Override
        protected void completed0(Void result, AioSocketChannel channel) {
            ((DefaultAioUnsafe) channel.unsafe()).connectSuccess();
        }

        @Override
        protected void failed0(Throwable exc, AioSocketChannel channel) {
            ((DefaultAioUnsafe) channel.unsafe()).connectFailed(exc);
        }
    }

    @Override
    public AioSocketChannelConfig config() {
        return config;
    }

    private final class WritableByteChannelAdapter implements WritableByteChannel {
        private final FlushTask task;
        private long written;

        public WritableByteChannelAdapter(FlushTask task) {
            this.task = task;
        }

        @Override
        public int write(final ByteBuffer src) {
            javaChannel().write(src, AioSocketChannel.this, new AioCompletionHandler<Integer, Channel>() {

                @Override
                public void completed0(Integer result, Channel attachment) {
                    try {
                        if (result == 0) {
                            javaChannel().write(src, AioSocketChannel.this, this);
                            return;
                        }
                        if (result == -1) {
                            checkEOF(task.region(), written);
                            task.setSuccess();
                            return;
                        }
                        written += result;

                        task.setProgress(written);

                        if (written >= task.region().count()) {
                            task.setSuccess();
                            return;
                        }
                        if (src.hasRemaining()) {
                            javaChannel().write(src, AioSocketChannel.this, this);
                        } else {
                            task.region().transferTo(WritableByteChannelAdapter.this, written);
                        }
                    } catch (Throwable cause) {
                        task.setFailure(cause);
                    }
                }

                @Override
                public void failed0(Throwable exc, Channel attachment) {
                    task.setFailure(exc);
                }
            });
            return 0;
        }

        @Override
        public boolean isOpen() {
            return javaChannel().isOpen();
        }

        @Override
        public void close() throws IOException {
            javaChannel().close();
        }
    }

}
