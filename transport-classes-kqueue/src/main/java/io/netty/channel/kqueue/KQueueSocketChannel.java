/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.channel.internal.ChannelUtils;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.SocketWritableByteChannel;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.LeakPresenceDetector;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

import static io.netty.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;
import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty.channel.unix.Socket.isIPv6Preferred;
import static io.netty.util.internal.StringUtil.className;

public final class KQueueSocketChannel extends AbstractKQueueChannel implements SocketChannel {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(KQueueSocketChannel.class);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
                    StringUtil.simpleClassName(DefaultFileRegion.class) + ')';
    private WritableByteChannel byteChannel;
    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            // Calling writeFlushed() directly to ensure we not try to flush messages that were added via write(...)
            // in the meantime.
            writeFlushed();
        }
    };

    private final KQueueSocketChannelConfig config;

    public KQueueSocketChannel(EventLoop eventLoop) {
        this(eventLoop, null, BsdSocket.newSocketStream(), false);
    }

    public KQueueSocketChannel(EventLoop eventLoop, SocketProtocolFamily protocol) {
        this(eventLoop, null, BsdSocket.newSocket(protocol), false);
    }

    public KQueueSocketChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new BsdSocket(fd, isIPv6Preferred() ?
                SocketProtocolFamily.INET6 : SocketProtocolFamily.INET));
    }

    KQueueSocketChannel(EventLoop eventLoop, Channel parent, BsdSocket fd, SocketAddress remoteAddress) {
        super(eventLoop, parent, fd, remoteAddress, false);
        config = new KQueueSocketChannelConfig(this);
    }

    KQueueSocketChannel(EventLoop eventLoop, Channel parent, BsdSocket fd, boolean active) {
        super(eventLoop, parent, fd, active, false);
        config = new KQueueSocketChannelConfig(this);
    }

    private KQueueSocketChannel(EventLoop eventLoop, BsdSocket fd) {
        this(eventLoop, null, fd, isSoErrorZero(fd));
    }

    @Override
    public SocketChannel read() {
        super.read();
        return this;
    }

    @Override
    public SocketChannel flush() {
        super.flush();
        return this;
    }

    @Override
    protected boolean isAllowHalfClosure() {
        return config.isAllowHalfClosure();
    }

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param in the collection which contains objects to write.
     * @param buf the {@link ByteBuf} from which the bytes should be written
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     */
    private int writeBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            in.remove();
            return 0;
        }

        if (buf.hasMemoryAddress() || buf.nioBufferCount() == 1) {
            return doWriteBytes(in, buf);
        } else {
            ByteBuffer[] nioBuffers = buf.nioBuffers();
            return writeBytesMultiple(in, nioBuffers, nioBuffers.length, readableBytes,
                    ((KQueueChannelConfig) config()).getMaxBytesPerGatheringWrite());
        }
    }

    private void adjustMaxBytesPerGatheringWrite(long attempted, long written, long oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.
        if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                ((KQueueChannelConfig) config()).setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            ((KQueueChannelConfig) config()).setMaxBytesPerGatheringWrite(attempted >>> 1);
        }
    }

    /**
     * Write multiple bytes via {@link IovArray}.
     * @param in the collection which contains objects to write.
     * @param array The array which contains the content to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws IOException If an I/O exception occurs during write.
     */
    private int writeBytesMultiple(ChannelOutboundBuffer in, IovArray array) throws IOException {
        final long expectedWrittenBytes = array.size();
        assert expectedWrittenBytes != 0;
        final int cnt = array.count();
        assert cnt != 0;

        final long localWrittenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
        if (localWrittenBytes > 0) {
            adjustMaxBytesPerGatheringWrite(expectedWrittenBytes, localWrittenBytes, array.maxBytes());
            in.removeBytes(localWrittenBytes);
            return 1;
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write multiple bytes via {@link ByteBuffer} array.
     * @param in the collection which contains objects to write.
     * @param nioBuffers The buffers to write.
     * @param nioBufferCnt The number of buffers to write.
     * @param expectedWrittenBytes The number of bytes we expect to write.
     * @param maxBytesPerGatheringWrite The maximum number of bytes we should attempt to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws IOException If an I/O exception occurs during write.
     */
    private int writeBytesMultiple(
            ChannelOutboundBuffer in, ByteBuffer[] nioBuffers, int nioBufferCnt, long expectedWrittenBytes,
            long maxBytesPerGatheringWrite) throws IOException {
        assert expectedWrittenBytes != 0;
        if (expectedWrittenBytes > maxBytesPerGatheringWrite) {
            expectedWrittenBytes = maxBytesPerGatheringWrite;
        }

        final long localWrittenBytes = socket.writev(nioBuffers, 0, nioBufferCnt, expectedWrittenBytes);
        if (localWrittenBytes > 0) {
            adjustMaxBytesPerGatheringWrite(expectedWrittenBytes, localWrittenBytes, maxBytesPerGatheringWrite);
            in.removeBytes(localWrittenBytes);
            return 1;
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write a {@link DefaultFileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link DefaultFileRegion} from which the bytes should be written
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     */
    private int writeDefaultFileRegion(ChannelOutboundBuffer in, DefaultFileRegion region) throws Exception {
        final long regionCount = region.count();
        final long offset = region.transferred();

        if (offset >= regionCount) {
            in.remove();
            return 0;
        }

        final long flushedAmount = socket.sendFile(region, region.position(), offset, regionCount - offset);
        if (flushedAmount > 0) {
            in.progress(flushedAmount);
            if (region.transferred() >= regionCount) {
                in.remove();
            }
            return 1;
        } else if (flushedAmount == 0) {
            validateFileRegion(region, offset);
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write a {@link FileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link FileRegion} from which the bytes should be written
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     */
    private int writeFileRegion(ChannelOutboundBuffer in, FileRegion region) throws Exception {
        if (region.transferred() >= region.count()) {
            in.remove();
            return 0;
        }

        if (byteChannel == null) {
            byteChannel = new KQueueSocketWritableByteChannel();
        }
        final long flushedAmount = region.transferTo(byteChannel, region.transferred());
        if (flushedAmount > 0) {
            in.progress(flushedAmount);
            if (region.transferred() >= region.count()) {
                in.remove();
            }
            return 1;
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        do {
            final int msgCount = in.size();
            // Do gathering write if the outbound buffer entries start with more than one ByteBuf.
            if (msgCount > 1 && in.current() instanceof ByteBuf) {
                writeSpinCount -= doWriteMultiple(in);
            } else if (msgCount == 0) {
                // Wrote all messages.
                writeFilter(false);
                // Return here so we don't set the WRITE flag.
                return;
            } else { // msgCount == 1
                writeSpinCount -= doWriteSingle(in);
            }

            // We do not break the loop here even if the outbound buffer was flushed completely,
            // because a user might have triggered another write and flush when we notify his or her
            // listeners.
        } while (writeSpinCount > 0);

        if (writeSpinCount == 0) {
            // It is possible that we have set the write filter, woken up by KQUEUE because the socket is writable, and
            // then use our write quantum. In this case we no longer want to set the write filter because the socket is
            // still writable (as far as we know). We will find out next time we attempt to write if the socket is
            // writable and set the write filter if necessary.
            writeFilter(false);

            // We used our writeSpin quantum, and should try to write again later.
            executor().execute(flushTask);
        } else {
            // Underlying descriptor can not accept all data currently, so set the WRITE flag to be woken up
            // when it can accept more data.
            writeFilter(true);
        }
    }

    /**
     * Attempt to write a single object.
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    protected int doWriteSingle(ChannelOutboundBuffer in) throws Exception {
        // The outbound buffer contains only one message or it contains a file region.
        Object msg = in.current();
        if (msg instanceof ByteBuf) {
            return writeBytes(in, (ByteBuf) msg);
        } else if (msg instanceof DefaultFileRegion) {
            return writeDefaultFileRegion(in, (DefaultFileRegion) msg);
        } else if (msg instanceof FileRegion) {
            return writeFileRegion(in, (FileRegion) msg);
        } else if (msg instanceof FileDescriptor && socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
            // File descriptor was written, so remove it.
            in.remove();
            return 1;
        } else {
            // Should never reach here.
            throw new Error("Unexpected message type: " + className(msg));
        }
    }

    /**
     * Attempt to write multiple {@link ByteBuf} objects.
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    private int doWriteMultiple(ChannelOutboundBuffer in) throws Exception {
        final long maxBytesPerGatheringWrite = ((KQueueChannelConfig) config()).getMaxBytesPerGatheringWrite();
        IovArray array = ((NativeArrays) registration().attachment()).cleanIovArray();
        array.maxBytes(maxBytesPerGatheringWrite);
        in.forEachFlushedMessage(array);

        if (array.count() >= 1) {
            // TODO: Handle the case where cnt == 1 specially.
            return writeBytesMultiple(in, array);
        }
        // cnt == 0, which means the outbound buffer contained empty buffers only.
        in.removeBytes(0);
        return 0;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        if (socket.protocolFamily() == SocketProtocolFamily.UNIX && msg instanceof FileDescriptor) {
            return msg;
        }
        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, ChannelPromise promise) {
        if (type.data() != null) {
            promise.setFailure(new IllegalArgumentException("ChannelShutdownType with data is not supported: " + type));
            return;
        }
        boolean read = false;
        boolean write = false;
        switch (type.direction()) {
            case Outbound:
                write = true;
                break;
            case Inbound:
                read = true;
                break;
        }
        try {
            socket.shutdown(read, write);
        } catch (NotYetConnectedException ex) {
            // We attempted to shutdown and failed, which means the input has already effectively been
            // shutdown.
        } catch (Throwable cause) {
            promise.setFailure(cause);
            return;
        }
        promise.setSuccess();
    }

    @Override
    void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX &&
                config.getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
            readReadyFd(allocHandle);
            return;
        }
        readReadyBytes(allocHandle);
    }

    private void readReadyFd(KQueueRecvByteAllocatorHandle allocHandle) {
        if (socket.isInputShutdown()) {
            super.clearReadFilter0();
            return;
        }
        final ChannelConfig config = config();

        final ChannelPipeline pipeline = pipeline();
        allocHandle.reset(config);

        try {
            readLoop: do {
                // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
                // KQueueRecvByteAllocatorHandle knows if it should try to read again or not when autoRead is
                // enabled.
                int recvFd = socket.recvFd();
                switch(recvFd) {
                    case 0:
                        allocHandle.lastBytesRead(0);
                        break readLoop;
                    case -1:
                        allocHandle.lastBytesRead(-1);
                        close(newPromise());
                        return;
                    default:
                        allocHandle.lastBytesRead(1);
                        allocHandle.incMessagesRead(1);
                        readPending = false;
                        pipeline.fireChannelRead(new FileDescriptor(recvFd));
                        break;
                }
            } while (allocHandle.continueReading());

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
        } catch (Throwable t) {
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(t);
        } finally {
            if (shouldStopReading(config)) {
                clearReadFilter0();
            }
        }
    }

    private void readReadyBytes(final KQueueRecvByteAllocatorHandle allocHandle) {
        final ChannelConfig config = config();
        if (shouldBreakReadReady()) {
            clearReadFilter0();
            return;
        }
        final ChannelPipeline pipeline = pipeline();
        final ByteBufAllocator allocator = config.getAllocator();
        allocHandle.reset(config);

        ByteBuf byteBuf = null;
        boolean close = false;
        try {
            do {
                // we use a direct buffer here as the native implementations only be able
                // to handle direct buffers.
                byteBuf = allocHandle.allocate(allocator);
                allocHandle.lastBytesRead(doReadBytes(byteBuf));
                if (allocHandle.lastBytesRead() <= 0) {
                    // nothing was read, release the buffer.
                    byteBuf.release();
                    byteBuf = null;
                    close = allocHandle.lastBytesRead() < 0;
                    if (close) {
                        // There is nothing left to read as we received an EOF.
                        readPending = false;
                    }
                    break;
                }
                allocHandle.incMessagesRead(1);
                readPending = false;
                pipeline.fireChannelRead(byteBuf);
                byteBuf = null;

                if (shouldBreakReadReady()) {
                    // We need to do this for two reasons:
                    //
                    // - If the input was shutdown in between (which may be the case when the user did it in the
                    //   fireChannelRead(...) method we should not try to read again to not produce any
                    //   miss-leading exceptions.
                    //
                    // - If the user closes the channel we need to ensure we not try to read from it again as
                    //   the filedescriptor may be re-used already by the OS if the system is handling a lot of
                    //   concurrent connections and so needs a lot of filedescriptors. If not do this we risk
                    //   reading data from a filedescriptor that belongs to another socket then the socket that
                    //   was "wrapped" by this Channel implementation.
                    break;
                }
            } while (allocHandle.continueReading());

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();

            if (close) {
                shutdownInput(false);
            }
        } catch (Throwable t) {
            handleReadException(pipeline, byteBuf, t, close, allocHandle);
        } finally {
            if (shouldStopReading(config)) {
                clearReadFilter0();
            }
        }
    }

    private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close,
                                     KQueueRecvByteAllocatorHandle allocHandle) {
        if (byteBuf != null) {
            if (byteBuf.isReadable()) {
                readPending = false;
                pipeline.fireChannelRead(byteBuf);
            } else {
                byteBuf.release();
            }
        }
        if (!failConnectPromise(cause)) {
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);

            // If oom will close the read event, release connection.
            // See https://github.com/netty/netty/issues/10434
            if (close ||
                    cause instanceof OutOfMemoryError ||
                    cause instanceof LeakPresenceDetector.AllocationProhibitedException ||
                    cause instanceof IOException) {
                shutdownInput(false);
            }
        }
    }

    private final class KQueueSocketWritableByteChannel extends SocketWritableByteChannel {
        KQueueSocketWritableByteChannel() {
            super(socket);
        }

        @Override
        protected ByteBufAllocator alloc() {
            return KQueueSocketChannel.this.alloc();
        }
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public ServerSocketChannel parent() {
        return (ServerSocketChannel) super.parent();
    }

    @Override
    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (config.isTcpFastOpenConnect()) {
            ChannelOutboundBuffer outbound = outboundBuffer();
            outbound.addFlush();
            Object curr;
            if ((curr = outbound.current()) instanceof ByteBuf) {
                ByteBuf initialData = (ByteBuf) curr;
                // Don't bother with TCP FastOpen if we don't have any initial data to send anyway.
                if (initialData.isReadable()) {
                    IovArray iov = new IovArray(config.getAllocator().directBuffer());
                    try {
                        iov.add(initialData, initialData.readerIndex(), initialData.readableBytes());
                        int bytesSent = socket.connectx(
                                (InetSocketAddress) localAddress, (InetSocketAddress) remoteAddress, iov, true);
                        writeFilter(true);
                        outbound.removeBytes(Math.abs(bytesSent));
                        // The `connectx` method returns a negative number if connection is in-progress.
                        // So we should return `true` to indicate that connection was established, if it's positive.
                        return bytesSent > 0;
                    } finally {
                        iov.release();
                    }
                }
            }
        }
        return super.doConnect0(remoteAddress, localAddress);
    }

    @Override
    protected Executor prepareToClose() {
        try {
            // Check isOpen() first as otherwise it will throw a RuntimeException
            // when call getSoLinger() as the fd is not valid anymore.
            if (isOpen() && config.getSoLinger() > 0) {
                // We need to cancel this key of the channel so we may not end up in a eventloop spin
                // because we try to read or write until the actual close happens which may be later due
                // SO_LINGER handling.
                // See https://github.com/netty/netty/issues/4449
                doDeregister(newPromise());
                return GlobalEventExecutor.INSTANCE;
            }
        } catch (Throwable ignore) {
            // Ignore the error as the underlying channel may be closed in the meantime and so
            // getSoLinger() may produce an exception. In this case we just return null.
            // See https://github.com/netty/netty/issues/4449
        }
        return null;
    }
}
