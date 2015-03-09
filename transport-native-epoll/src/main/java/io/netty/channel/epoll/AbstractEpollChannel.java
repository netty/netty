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
import io.netty.channel.ChannelMetadata;
import io.netty.channel.EventLoop;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.OneTimeTask;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;

abstract class AbstractEpollChannel extends AbstractChannel implements UnixChannel {
    private static final ChannelMetadata DATA = new ChannelMetadata(false);
    private final int readFlag;
    private final FileDescriptor fileDescriptor;
    protected int flags = Native.EPOLLET;

    protected volatile boolean active;

    AbstractEpollChannel(int fd, int flag) {
        this(null, fd, flag, false);
    }

    AbstractEpollChannel(Channel parent, int fd, int flag, boolean active) {
        this(parent, new FileDescriptor(fd), flag, active);
    }

    AbstractEpollChannel(Channel parent, FileDescriptor fd, int flag, boolean active) {
        super(parent);
        if (fd == null) {
            throw new NullPointerException("fd");
        }
        readFlag = flag;
        flags |= flag;
        this.active = active;
        fileDescriptor = fd;
    }

    void setFlag(int flag) {
        if (!isFlagSet(flag)) {
            flags |= flag;
            modifyEvents();
        }
    }

    void clearFlag(int flag) {
        if (isFlagSet(flag)) {
            flags &= ~flag;
            modifyEvents();
        }
    }

    boolean isFlagSet(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public final FileDescriptor fd() {
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
        return DATA;
    }

    @Override
    protected void doClose() throws Exception {
        active = false;

        // deregister from epoll now
        doDeregister();

        FileDescriptor fd = fileDescriptor;
        fd.close();
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
        ((EpollEventLoop) eventLoop().unwrap()).remove(this);
    }

    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        ((AbstractEpollUnsafe) unsafe()).readPending = true;

        setFlag(readFlag);
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
                loop.execute(new OneTimeTask() {
                    @Override
                    public void run() {
                        if (!config().isAutoRead() && !unsafe.readPending) {
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

    private void modifyEvents() {
        if (isOpen() && isRegistered()) {
            ((EpollEventLoop) eventLoop().unwrap()).modify(this);
        }
    }

    @Override
    protected void doRegister() throws Exception {
        ((EpollEventLoop) eventLoop().unwrap()).add(this);
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
        if (byteBuf.hasMemoryAddress()) {
            localReadAmount = Native.readAddress(
                    fileDescriptor.intValue(), byteBuf.memoryAddress(), writerIndex, byteBuf.capacity());
        } else {
            ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, byteBuf.writableBytes());
            localReadAmount = Native.read(fileDescriptor.intValue(), buf, buf.position(), buf.limit());
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
                int localFlushedAmount = Native.writeAddress(
                        fileDescriptor.intValue(), memoryAddress, readerIndex, writerIndex);
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
                int localFlushedAmount = Native.write(fileDescriptor.intValue(), nioBuf, pos, limit);
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
        protected boolean readPending;

        /**
         * Called once EPOLLIN event is ready to be processed
         */
        abstract void epollInReady();

        /**
         * Called once EPOLLRDHUP event is ready to be processed
         */
        void epollRdHupReady() {
            // NOOP
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
            // directly call super.flush0() to force a flush now
            super.flush0();
        }

        protected final void clearEpollIn0() {
            clearFlag(readFlag);
        }
    }
}
