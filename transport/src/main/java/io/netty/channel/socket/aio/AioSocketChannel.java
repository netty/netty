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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class AioSocketChannel extends AbstractAioChannel implements SocketChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.BYTE, false);

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

    private final AioSocketChannelConfig config;
    private boolean flushing;

    private final AtomicBoolean readSuspended = new AtomicBoolean();
    private final AtomicBoolean readInProgress = new AtomicBoolean();

    private final Runnable readTask = new Runnable() {
        @Override
        public void run() {
            AioSocketChannel.this.beginRead();
        }
    };

    public AioSocketChannel(AioEventLoopGroup eventLoop) {
        this(null, null, eventLoop, newSocket(eventLoop.group));
    }

    AioSocketChannel(
            AioServerSocketChannel parent, Integer id,
            AioEventLoopGroup eventLoop, AsynchronousSocketChannel ch) {
        super(parent, id, eventLoop, ch);
        config = new AioSocketChannelConfig(ch);
    }

    @Override
    public boolean isActive() {
        return javaChannel().isOpen() && remoteAddress0() != null;
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
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, final ChannelFuture future) {
        if (localAddress != null) {
            try {
                javaChannel().bind(localAddress);
            } catch (IOException e) {
                future.setFailure(e);
                return;
            }
        }

        javaChannel().connect(remoteAddress, this, CONNECT_HANDLER);
    }

    @Override
    protected InetSocketAddress localAddress0() {
        try {
            return (InetSocketAddress) javaChannel().getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected InetSocketAddress remoteAddress0() {
        try {
            return (InetSocketAddress) javaChannel().getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Runnable doRegister() throws Exception {
        super.doRegister();

        if (remoteAddress() == null) {
            return null;
        }

        return new Runnable() {
            @Override
            public void run() {
                beginRead();
            }
        };
    }

    private static boolean expandReadBuffer(ByteBuf byteBuf) {
        if (!byteBuf.writable()) {
            // FIXME: Magic number
            byteBuf.ensureWritableBytes(4096);
            return true;
        }
        return false;
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
    }

    @Override
    protected boolean isFlushPending() {
        return false;
    }

    @Override
    protected void doFlushByteBuffer(ByteBuf buf) throws Exception {
        if (flushing) {
            return;
        }

        flushing = true;

        // Ensure the readerIndex of the buffer is 0 before beginning an async write.
        // Otherwise, JDK can write into a wrong region of the buffer when a handler calls
        // discardReadBytes() later, modifying the readerIndex and the writerIndex unexpectedly.
        buf.discardReadBytes();

        if (buf.readable()) {
            if (buf.hasNioBuffers()) {
                ByteBuffer[] buffers = buf.nioBuffers(buf.readerIndex(), buf.readableBytes());
                javaChannel().write(buffers, 0, buffers.length, config.getReadTimeout(),
                        TimeUnit.MILLISECONDS, AioSocketChannel.this, GATHERING_WRITE_HANDLER);
            } else {
                javaChannel().write(buf.nioBuffer(), config.getReadTimeout(), TimeUnit.MILLISECONDS,
                        this, WRITE_HANDLER);
            }
        } else {
            notifyFlushFutures();
            flushing = false;
        }
    }

    private void beginRead() {
        if (readSuspended.get()) {
            return;
        }

        // prevent ReadPendingException
        if (!readInProgress.compareAndSet(false, true)) {
            return;
        }

        ByteBuf byteBuf = pipeline().inboundByteBuffer();
        if (!byteBuf.readable()) {
            byteBuf.discardReadBytes();
        } else {
            expandReadBuffer(byteBuf);
        }

        if (byteBuf.hasNioBuffers()) {
            ByteBuffer[] buffers = byteBuf.nioBuffers(byteBuf.writerIndex(), byteBuf.writableBytes());
            javaChannel().read(buffers, 0, buffers.length, config.getWriteTimeout(),
                    TimeUnit.MILLISECONDS, AioSocketChannel.this, SCATTERING_READ_HANDLER);
        } else {
            // Get a ByteBuffer view on the ByteBuf
            ByteBuffer buffer = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
            javaChannel().read(buffer, config.getWriteTimeout(), TimeUnit.MILLISECONDS,
                    AioSocketChannel.this, READ_HANDLER);
        }
    }

    private static final class WriteHandler<T extends Number> extends AioCompletionHandler<T, AioSocketChannel> {

        @Override
        protected void completed0(T result, AioSocketChannel channel) {
            ByteBuf buf = channel.unsafe().directOutboundContext().outboundByteBuffer();
            int writtenBytes = result.intValue();
            if (writtenBytes > 0) {
                // Update the readerIndex with the amount of read bytes
                buf.readerIndex(buf.readerIndex() + writtenBytes);
            }

            boolean empty = !buf.readable();

            if (empty) {
                // Reset reader/writerIndex to 0 if the buffer is empty.
                buf.discardReadBytes();
            }

            channel.notifyFlushFutures(writtenBytes);

            // Allow to have the next write pending
            channel.flushing = false;

            // Stop flushing if disconnected.
            if (!channel.isActive()) {
                return;
            }

            if (buf.readable()) {
                try {
                    // Try to flush it again.
                    channel.doFlushByteBuffer(buf);
                } catch (Exception e) {
                    // Should never happen, anyway call failed just in case
                    failed0(e, channel);
                }
            }
        }

        @Override
        protected void failed0(Throwable cause, AioSocketChannel channel) {
            channel.notifyFlushFutures(cause);
            channel.pipeline().fireExceptionCaught(cause);

            // Check if the exception was raised because of an InterruptedByTimeoutException which means that the
            // write timeout was hit. In that case we should close the channel as it may be unusable anyway.
            //
            // See http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousSocketChannel.html
            if (cause instanceof InterruptedByTimeoutException) {
                channel.unsafe().close(channel.unsafe().voidFuture());
                return;
            }

            ByteBuf buf = channel.unsafe().directOutboundContext().outboundByteBuffer();
            if (!buf.readable()) {
                buf.discardReadBytes();
            }

            // Allow to have the next write pending
            channel.flushing = false;
        }
    }

    private static final class ReadHandler<T extends Number> extends AioCompletionHandler<T, AioSocketChannel> {

        @Override
        protected void completed0(T result, AioSocketChannel channel) {
            final ChannelPipeline pipeline = channel.pipeline();
            final ByteBuf byteBuf = pipeline.inboundByteBuffer();

            boolean closed = false;
            boolean read = false;
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
                    if (!channel.readSuspended.get()) {
                        pipeline.fireInboundBufferUpdated();
                    }
                }

                if (!(t instanceof ClosedChannelException)) {
                    pipeline.fireExceptionCaught(t);

                    if (t instanceof IOException) {
                        channel.unsafe().close(channel.unsafe().voidFuture());
                    }
                }
            } finally {
                // see beginRead
                channel.readInProgress.set(false);

                if (read) {
                    if (!channel.readSuspended.get()) {
                        pipeline.fireInboundBufferUpdated();
                    }
                }
                if (closed && channel.isOpen()) {
                    channel.unsafe().close(channel.unsafe().voidFuture());
                } else {
                    // start the next read
                    channel.beginRead();
                }
            }
        }

        @Override
        protected void failed0(Throwable t, AioSocketChannel channel) {
            if (t instanceof ClosedChannelException) {
                return;
            }

            channel.pipeline().fireExceptionCaught(t);

            // Check if the exception was raised because of an InterruptedByTimeoutException which means that the
            // write timeout was hit. In that case we should close the channel as it may be unusable anyway.
            //
            // See http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousSocketChannel.html
            if (t instanceof IOException || t instanceof InterruptedByTimeoutException) {
                channel.unsafe().close(channel.unsafe().voidFuture());
            } else {
                // start the next read
                channel.beginRead();
            }
        }
    }

    private static final class ConnectHandler extends AioCompletionHandler<Void, AioSocketChannel> {

        @Override
        protected void completed0(Void result, AioSocketChannel channel) {
            ((AbstractAioUnsafe) channel.unsafe()).connectSuccess();
            channel.pipeline().fireChannelActive();
            channel.beginRead();
        }

        @Override
        protected void failed0(Throwable exc, AioSocketChannel channel) {
            ((AbstractAioUnsafe) channel.unsafe()).connectFailed(exc);
        }
    }

    @Override
    public AioSocketChannelConfig config() {
        return config;
    }

    @Override
    protected Unsafe newUnsafe() {
        return new AioSocketChannelAsyncUnsafe();
    }

    private final class AioSocketChannelAsyncUnsafe extends AbstractAioUnsafe {

        @Override
        public void suspendRead() {
            readSuspended.set(true);
        }

        @Override
        public void resumeRead() {
            if (readSuspended.compareAndSet(true, false)) {
                if (eventLoop().inEventLoop()) {
                    beginRead();
                } else {
                    eventLoop().execute(readTask);
                }
            }
        }
    }
}
