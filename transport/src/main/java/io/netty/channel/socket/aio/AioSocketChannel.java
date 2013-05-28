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
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageList;
import io.netty.channel.aio.AbstractAioChannel;
import io.netty.channel.aio.AioCompletionHandler;
import io.netty.channel.aio.AioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.internal.PlatformDependent;

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

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final CompletionHandler<Void, Void> connectHandler  = new ConnectHandler(this);
    private final CompletionHandler<Integer, ByteBuf> writeHandler = new WriteHandler<Integer>(this);
    private final CompletionHandler<Integer, ByteBuf> readHandler = new ReadHandler<Integer>(this);
    private final CompletionHandler<Long, ByteBuf> gatheringWriteHandler = new WriteHandler<Long>(this);
    private final CompletionHandler<Long, ByteBuf> scatteringReadHandler = new ReadHandler<Long>(this);

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

    private Throwable writeException;
    private boolean writeInProgress;
    private boolean inDoWrite;
    private boolean fileRegionDone;

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

        javaChannel().connect(remoteAddress, null, connectHandler);
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
    protected int doWrite(MessageList<Object> msgs, int index) throws Exception {
        if (inDoWrite || writeInProgress) {
            return 0;
        }
        inDoWrite = true;

        try {
            Object msg = msgs.get(index);
            if (msg instanceof ByteBuf) {
                if (doWriteBuffer((ByteBuf) msg)) {
                    return 1;
                }
                return 0;
            }
            if (msg instanceof FileRegion) {
                if (doWriteFileRegion((FileRegion) msg)) {
                    return 1;
                }
                return 0;
            }
        } finally {
            inDoWrite = false;
        }
        return 0;
    }

    private boolean doWriteBuffer(ByteBuf buf) throws Exception {
        if (buf.isReadable()) {
            for (;;) {
                checkWriteException();
                writeInProgress = true;
                if (buf.nioBufferCount() == 1) {
                    javaChannel().write(
                            buf.nioBuffer(), config.getWriteTimeout(), TimeUnit.MILLISECONDS,
                            buf, writeHandler);
                } else {
                    ByteBuffer[] buffers = buf.nioBuffers(buf.readerIndex(), buf.readableBytes());
                    if (buffers.length == 1) {
                        javaChannel().write(
                                buffers[0], config.getWriteTimeout(), TimeUnit.MILLISECONDS, buf, writeHandler);
                    } else {
                        javaChannel().write(
                                buffers, 0, buffers.length, config.getWriteTimeout(), TimeUnit.MILLISECONDS,
                                buf, gatheringWriteHandler);
                    }
                }

                if (writeInProgress) {
                    // JDK decided to write data (or notify handler) later.
                    return false;
                }
                checkWriteException();

                // JDK performed the write operation immediately and notified the handler.
                // We know this because we set asyncWriteInProgress to false in the handler.
                if (!buf.isReadable()) {
                    // There's nothing left in the buffer. No need to retry writing.
                    return true;
                }

                // There's more to write. Continue the loop.
            }
        }
        return true;
    }

    private boolean doWriteFileRegion(FileRegion region) throws Exception {
        checkWriteException();

        if (fileRegionDone) {
            // fileregion was complete in the CompletionHandler
            fileRegionDone = false;
            // was written complete
            return true;
        }

        WritableByteChannelAdapter byteChannel = new WritableByteChannelAdapter(region);
        region.transferTo(byteChannel, 0);

        // check if the FileRegion is already complete. This may be the case if all could be written directly
        if (byteChannel.written >= region.count()) {
            return true;
        }
        return false;
    }

    private void checkWriteException() throws Exception {
        if (writeException != null) {
            fileRegionDone = false;
            Throwable e = writeException;
            writeException = null;
            PlatformDependent.throwException(e);
        }
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

                ByteBuf byteBuf = alloc().buffer();

                readInProgress = true;
                if (byteBuf.nioBufferCount() == 1) {
                    // Get a ByteBuffer view on the ByteBuf
                    ByteBuffer buffer = byteBuf.nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
                    javaChannel().read(
                            buffer, config.getReadTimeout(), TimeUnit.MILLISECONDS, byteBuf, readHandler);
                } else {
                    ByteBuffer[] buffers = byteBuf.nioBuffers(byteBuf.writerIndex(), byteBuf.writableBytes());
                    if (buffers.length == 1) {
                        javaChannel().read(
                                buffers[0], config.getReadTimeout(), TimeUnit.MILLISECONDS, byteBuf, readHandler);
                    } else {
                        javaChannel().read(
                                buffers, 0, buffers.length, config.getReadTimeout(), TimeUnit.MILLISECONDS,
                                byteBuf, scatteringReadHandler);
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

    private void setWriteException(Throwable cause) {
        writeException = cause;
    }

    private static final class WriteHandler<T extends Number>
            extends AioCompletionHandler<AioSocketChannel, T, ByteBuf> {

        WriteHandler(AioSocketChannel channel) {
            super(channel);
        }

        @Override
        protected void completed0(AioSocketChannel channel, T result, ByteBuf buf) {
            channel.writeException = null;
            channel.writeInProgress = false;
            boolean release = true;
            try {
                int writtenBytes = result.intValue();
                if (writtenBytes > 0) {
                    // Update the readerIndex with the amount of read bytes
                    buf.readerIndex(buf.readerIndex() + writtenBytes);
                }
                if (buf.isReadable()) {
                    // something left in the buffer so not release it
                    release = false;
                }
            } finally {
                if (release) {
                    buf.release();
                }

                if (channel.inDoWrite) {
                    // JDK performed the write operation immediately and notified this handler immediately.
                    // doWrite(...) will do subsequent write operations if necessary for us.
                } else {
                    // trigger flush so doWrite(..) is called again. This will either trigger a new write to the
                    // channel or remove the empty bytebuf (which was written completely before) from the MessageList.
                    channel.unsafe().flushNow();
                }
            }
        }

        @Override
        protected void failed0(AioSocketChannel channel, Throwable cause, ByteBuf buf) {
            buf.release();
            channel.setWriteException(cause);
            channel.writeInProgress = false;

            // Check if the exception was raised because of an InterruptedByTimeoutException which means that the
            // write timeout was hit. In that case we should close the channel as it may be unusable anyway.
            //
            // See http://openjdk.java.net/projects/nio/javadoc/java/nio/channels/AsynchronousSocketChannel.html
            if (cause instanceof InterruptedByTimeoutException) {
                channel.unsafe().close(channel.unsafe().voidPromise());
            }

            if (!channel.inDoWrite) {
                // trigger flushNow() so the Throwable is thrown in doWrite(...). This will make sure that all
                // queued MessageLists are failed.
                channel.unsafe().flushNow();
            }
        }
    }

    private final class ReadHandler<T extends Number> extends AioCompletionHandler<AioSocketChannel, T, ByteBuf> {

        ReadHandler(AioSocketChannel channel) {
            super(channel);
        }

        @Override
        protected void completed0(AioSocketChannel channel, T result, ByteBuf byteBuf) {
            channel.readInProgress = false;

            if (channel.inputShutdown) {
                // Channel has been closed during read. Because the inbound buffer has been deallocated already,
                // there's no way to let a user handler access it unfortunately.
                return;
            }

            boolean release = true;
            final ChannelPipeline pipeline = channel.pipeline();
            try {
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
                        release = false;
                        pipeline.fireMessageReceived(byteBuf);
                    }

                    if (!closed && isOpen()) {
                        firedChannelReadSuspended = true;
                        pipeline.fireChannelReadSuspended();
                    }

                    pipeline.fireExceptionCaught(t);
                } finally {
                    if (read) {
                        release = false;
                        pipeline.fireMessageReceived(byteBuf);
                    }

                    // Double check because fireInboundBufferUpdated() might have triggered
                    // the closure by a user handler.
                    if (closed || !channel.isOpen()) {
                        channel.inputShutdown = true;
                        if (isOpen()) {
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
            } finally {
                if (release) {
                    byteBuf.release();
                }
            }
        }

        @Override
        protected void failed0(AioSocketChannel channel, Throwable t, ByteBuf buf) {
            buf.release();

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

    private static final class ConnectHandler extends AioCompletionHandler<AioSocketChannel, Void, Void> {
        ConnectHandler(AioSocketChannel channel) {
            super(channel);
        }

        @Override
        protected void completed0(AioSocketChannel channel, Void result, Void attachment) {
            ((DefaultAioUnsafe) channel.unsafe()).connectSuccess();
        }

        @Override
        protected void failed0(AioSocketChannel channel, Throwable exc, Void attachment) {
            ((DefaultAioUnsafe) channel.unsafe()).connectFailed(exc);
        }
    }

    @Override
    public AioSocketChannelConfig config() {
        return config;
    }

    private final class WritableByteChannelAdapter implements WritableByteChannel {
        private final FileRegionWriteHandler handler = new FileRegionWriteHandler();
        private final FileRegion region;
        private long written;

        public WritableByteChannelAdapter(FileRegion region) {
            this.region = region;
        }

        @Override
        public int write(final ByteBuffer src) {
            javaChannel().write(src, src, handler);
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

        private final class FileRegionWriteHandler extends AioCompletionHandler<AioSocketChannel, Integer, ByteBuffer> {

            FileRegionWriteHandler() {
                super(AioSocketChannel.this);
            }

            @Override
            public void completed0(AioSocketChannel channel, Integer result, ByteBuffer src) {
                try {
                    assert !fileRegionDone;

                    if (result == -1) {
                        checkEOF(region);
                        // mark the region as done and release it
                        fileRegionDone = true;
                        region.release();
                        return;
                    }

                    written += result;

                    if (written >= region.count()) {
                        channel.writeInProgress = false;

                        // mark the region as done and release it
                        fileRegionDone = true;
                        region.release();
                        return;
                    }
                    if (src.hasRemaining()) {
                        // something left in the buffer trigger a write again
                        javaChannel().write(src, src, this);
                    } else {
                        // everything was written out of the src buffer, so trigger a new transfer with new data
                        region.transferTo(WritableByteChannelAdapter.this, written);
                    }
                } catch (Throwable cause) {
                    failed0(channel, cause, src);
                }
            }

            @Override
            public void failed0(AioSocketChannel channel, Throwable cause, ByteBuffer src) {
                assert !fileRegionDone;

                // mark the region as done and release it
                fileRegionDone = true;
                region.release();
                channel.setWriteException(cause);
                if (!inDoWrite) {
                    // not executed as part of the doWrite(...) so trigger flushNow() to make sure the doWrite(...)
                    // will be called again and so rethrow the exception
                    channel.unsafe().flushNow();
                }
            }
        }
    }
}
