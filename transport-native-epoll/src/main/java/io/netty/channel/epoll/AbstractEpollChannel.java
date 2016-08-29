/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannel;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

abstract class AbstractEpollChannel extends AbstractChannel implements UnixChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final int readFlag;
    private final Socket fileDescriptor;
    protected int flags = Native.EPOLLET;

    protected volatile boolean active;

    AbstractEpollChannel(Socket fd, int flag) {
        this(null, fd, flag, false);
    }

    AbstractEpollChannel(Channel parent, Socket fd, int flag, boolean active) {
        super(parent);
        fileDescriptor = checkNotNull(fd, "fd");
        readFlag = flag;
        flags |= flag;
        this.active = active;
    }

    static boolean isSoErrorZero(Socket fd) {
        try {
            return fd.getSoError() == 0;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    void setFlag(int flag) throws IOException {
        if (!isFlagSet(flag)) {
            flags |= flag;
            modifyEvents();
        }
    }

    void clearFlag(int flag) throws IOException {
        if (isFlagSet(flag)) {
            flags &= ~flag;
            modifyEvents();
        }
    }

    boolean isFlagSet(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public final Socket fd() {
        return fileDescriptor;
    }

    @Override
    public abstract EpollChannelConfig config();

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected void doClose() throws Exception {
        active = false;
        try {
            doDeregister();
        } finally {
            fileDescriptor.close();
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return loop instanceof EpollEventLoop;
    }

    @Override
    public boolean isOpen() {
        return fileDescriptor.isOpen();
    }

    @Override
    protected void doDeregister() throws Exception {
        ((EpollEventLoop) eventLoop()).remove(this);
    }

    @Override
    protected final void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final AbstractEpollUnsafe unsafe = (AbstractEpollUnsafe) unsafe();
        unsafe.readPending = true;

        // We must set the read flag here as it is possible the user didn't read in the last read loop, the
        // executeEpollInReadyRunnable could read nothing, and if the user doesn't explicitly call read they will
        // never get data after this.
        setFlag(readFlag);

        // If EPOLL ET mode is enabled and auto read was toggled off on the last read loop then we may not be notified
        // again if we didn't consume all the data. So we force a read operation here if there maybe more data.
        if (unsafe.maybeMoreDataToRead) {
            unsafe.executeEpollInReadyRunnable();
        }
    }

    final void clearEpollIn() {
        // Only clear if registered with an EventLoop as otherwise
        if (isRegistered()) {
            final EventLoop loop = eventLoop();
            final AbstractEpollUnsafe unsafe = (AbstractEpollUnsafe) unsafe();
            if (loop.inEventLoop()) {
                unsafe.clearEpollIn0();
            } else {
                // schedule a task to clear the EPOLLIN as it is not safe to modify it directly
                loop.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!unsafe.readPending && !config().isAutoRead()) {
                            // Still no read triggered so clear it now
                            unsafe.clearEpollIn0();
                        }
                    }
                });
            }
        } else  {
            // The EventLoop is not registered atm so just update the flags so the correct value
            // will be used once the channel is registered
            flags &= ~readFlag;
        }
    }

    private void modifyEvents() throws IOException {
        if (isOpen() && isRegistered()) {
            ((EpollEventLoop) eventLoop()).modify(this);
        }
    }

    @Override
    protected void doRegister() throws Exception {
        EpollEventLoop loop = (EpollEventLoop) eventLoop();
        // Just in case the previous EventLoop was shutdown abruptly, or an event is still pending on the old EventLoop
        // make sure the epollInReadyRunnablePending variable is reset so we will be able to execute the Runnable on the
        // new EventLoop.
        ((AbstractEpollUnsafe) unsafe()).epollInReadyRunnablePending = false;
        loop.add(this);
    }

    @Override
    protected abstract AbstractEpollUnsafe newUnsafe();

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the original one.
     */
    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        return newDirectBuffer(buf, buf);
    }

    /**
     * Returns an off-heap copy of the specified {@link ByteBuf}, and releases the specified holder.
     * The caller must ensure that the holder releases the original {@link ByteBuf} when the holder is released by
     * this method.
     */
    protected final ByteBuf newDirectBuffer(Object holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.safeRelease(holder);
            return Unpooled.EMPTY_BUFFER;
        }

        final ByteBufAllocator alloc = alloc();
        if (alloc.isDirectBufferPooled()) {
            return newDirectBuffer0(holder, buf, alloc, readableBytes);
        }

        final ByteBuf directBuf = ByteBufUtil.threadLocalDirectBuffer();
        if (directBuf == null) {
            return newDirectBuffer0(holder, buf, alloc, readableBytes);
        }

        directBuf.writeBytes(buf, buf.readerIndex(), readableBytes);
        ReferenceCountUtil.safeRelease(holder);
        return directBuf;
    }

    private static ByteBuf newDirectBuffer0(Object holder, ByteBuf buf, ByteBufAllocator alloc, int capacity) {
        final ByteBuf directBuf = alloc.directBuffer(capacity);
        directBuf.writeBytes(buf, buf.readerIndex(), capacity);
        ReferenceCountUtil.safeRelease(holder);
        return directBuf;
    }

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    protected final int doReadBytes(ByteBuf byteBuf) throws Exception {
        int writerIndex = byteBuf.writerIndex();
        int localReadAmount;
        unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());
        if (byteBuf.hasMemoryAddress()) {
            localReadAmount = fileDescriptor.readAddress(byteBuf.memoryAddress(), writerIndex, byteBuf.capacity());
        } else {
            ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, byteBuf.writableBytes());
            localReadAmount = fileDescriptor.read(buf, buf.position(), buf.limit());
        }
        if (localReadAmount > 0) {
            byteBuf.writerIndex(writerIndex + localReadAmount);
        }
        return localReadAmount;
    }

    protected final int doWriteBytes(ByteBuf buf, int writeSpinCount) throws Exception {
        int readableBytes = buf.readableBytes();
        int writtenBytes = 0;
        if (buf.hasMemoryAddress()) {
            long memoryAddress = buf.memoryAddress();
            int readerIndex = buf.readerIndex();
            int writerIndex = buf.writerIndex();
            for (int i = writeSpinCount - 1; i >= 0; i--) {
                int localFlushedAmount = fileDescriptor.writeAddress(memoryAddress, readerIndex, writerIndex);
                if (localFlushedAmount > 0) {
                    writtenBytes += localFlushedAmount;
                    if (writtenBytes == readableBytes) {
                        return writtenBytes;
                    }
                    readerIndex += localFlushedAmount;
                } else {
                    break;
                }
            }
        } else {
            ByteBuffer nioBuf;
            if (buf.nioBufferCount() == 1) {
                nioBuf = buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes());
            } else {
                nioBuf = buf.nioBuffer();
            }
            for (int i = writeSpinCount - 1; i >= 0; i--) {
                int pos = nioBuf.position();
                int limit = nioBuf.limit();
                int localFlushedAmount = fileDescriptor.write(nioBuf, pos, limit);
                if (localFlushedAmount > 0) {
                    nioBuf.position(pos + localFlushedAmount);
                    writtenBytes += localFlushedAmount;
                    if (writtenBytes == readableBytes) {
                        return writtenBytes;
                    }
                } else {
                    break;
                }
            }
        }
        if (writtenBytes < readableBytes) {
            // Returned EAGAIN need to set EPOLLOUT
            setFlag(Native.EPOLLOUT);
        }
        return writtenBytes;
    }

    protected abstract class AbstractEpollUnsafe extends AbstractUnsafe {
        boolean readPending;
        boolean maybeMoreDataToRead;
        boolean epollInReadyRunnablePending;
        private EpollRecvByteAllocatorHandle allocHandle;
        private Runnable epollInReadyRunnable;

        /**
         * Called once EPOLLIN event is ready to be processed
         */
        abstract void epollInReady();

        final void epollInBefore() { maybeMoreDataToRead = false; }

        final void epollInFinally(ChannelConfig config) {
            maybeMoreDataToRead = allocHandle.maybeMoreDataToRead();
            // Check if there is a readPending which was not processed yet.
            // This could be for two reasons:
            // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
            // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
            //
            // See https://github.com/netty/netty/issues/2254
            if (!readPending && !config.isAutoRead()) {
                clearEpollIn();
            } else if (readPending && maybeMoreDataToRead && !fd().isInputShutdown()) {
                // trigger a read again as there may be something left to read and because of epoll ET we
                // will not get notified again until we read everything from the socket
                //
                // It is possible the last fireChannelRead call could cause the user to call read() again, or if
                // autoRead is true the call to channelReadComplete would also call read, but maybeMoreDataToRead is set
                // to false before every read operation to prevent re-entry into epollInReady() we will not read from
                // the underlying OS again unless the user happens to call read again.
                executeEpollInReadyRunnable();
            }
        }

        final void executeEpollInReadyRunnable() {
            if (epollInReadyRunnablePending) {
                return;
            }
            epollInReadyRunnablePending = true;
            if (epollInReadyRunnable == null) {
                epollInReadyRunnable = new Runnable() {
                    @Override
                    public void run() {
                        epollInReadyRunnablePending = false;
                        epollInReady();
                    }
                };
            }
            eventLoop().execute(epollInReadyRunnable);
        }

        /**
         * Called once EPOLLRDHUP event is ready to be processed
         */
        final void epollRdHupReady() {
            // This must happen before we attempt to read. This will ensure reading continues until an error occurs.
            recvBufAllocHandle().receivedRdHup();

            if (isActive()) {
                // If it is still active, we need to call epollInReady as otherwise we may miss to
                // read pending data from the underlying file descriptor.
                // See https://github.com/netty/netty/issues/3709
                epollInReady();

                // Clear the EPOLLRDHUP flag to prevent continuously getting woken up on this event.
                clearEpollRdHup();
            }

            // epollInReady may call this, but we should ensure that it gets called.
            shutdownInput();
        }

        /**
         * Clear the {@link Native#EPOLLRDHUP} flag from EPOLL, and close on failure.
         */
        private void clearEpollRdHup() {
            try {
                clearFlag(Native.EPOLLRDHUP);
            } catch (IOException e) {
                pipeline().fireExceptionCaught(e);
                close(voidPromise());
            }
        }

        /**
         * Shutdown the input side of the channel.
         */
        void shutdownInput() {
            if (!fd().isInputShutdown()) {
                if (Boolean.TRUE.equals(config().getOption(ChannelOption.ALLOW_HALF_CLOSURE))) {
                    try {
                        fd().shutdown(true, false);
                        clearEpollIn0();
                    } catch (IOException ignored) {
                        // We attempted to shutdown and failed, which means the input has already effectively been
                        // shutdown.
                        fireEventAndClose(ChannelInputShutdownEvent.INSTANCE);
                        return;
                    } catch (NotYetConnectedException ignore) {
                        // We attempted to shutdown and failed, which means the input has already effectively been
                        // shutdown.
                        fireEventAndClose(ChannelInputShutdownEvent.INSTANCE);
                        return;
                    }
                    pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            }
        }

        private void fireEventAndClose(Object evt) {
            pipeline().fireUserEventTriggered(evt);
            close(voidPromise());
        }

        @Override
        public EpollRecvByteAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = newEpollHandle(super.recvBufAllocHandle());
            }
            return allocHandle;
        }

        /**
         * Create a new {@link EpollRecvByteAllocatorHandle} instance.
         * @param handle The handle to wrap with EPOLL specific logic.
         */
        EpollRecvByteAllocatorHandle newEpollHandle(RecvByteBufAllocator.Handle handle) {
            return new EpollRecvByteAllocatorHandle(handle, config());
        }

        @Override
        protected void flush0() {
            // Flush immediately only when there's no pending flush.
            // If there's a pending flush operation, event loop will call forceFlush() later,
            // and thus there's no need to call it now.
            if (isFlagSet(Native.EPOLLOUT)) {
                return;
            }
            super.flush0();
        }

        /**
         * Called once a EPOLLOUT event is ready to be processed
         */
        void epollOutReady() {
            if (fd().isOutputShutdown()) {
                return;
            }
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        protected final void clearEpollIn0() {
            assert eventLoop().inEventLoop();
            try {
                readPending = false;
                clearFlag(readFlag);
            } catch (IOException e) {
                // When this happens there is something completely wrong with either the filedescriptor or epoll,
                // so fire the exception through the pipeline and close the Channel.
                pipeline().fireExceptionCaught(e);
                unsafe().close(unsafe().voidPromise());
            }
        }
    }
}
