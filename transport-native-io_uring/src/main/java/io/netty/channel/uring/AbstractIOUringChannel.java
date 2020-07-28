/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.UnixChannel;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.util.internal.ObjectUtil.*;

abstract class AbstractIOUringChannel extends AbstractChannel implements UnixChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    final Socket socket;
    protected volatile boolean active;
    boolean uringInReadyPending;

    private volatile SocketAddress local;
    private volatile SocketAddress remote;

    AbstractIOUringChannel(final Channel parent, Socket socket) {
        super(parent);
        this.socket = checkNotNull(socket, "fd");
        this.active = true;

        if (active) {
            // Directly cache the remote and local addresses
            // See https://github.com/netty/netty/issues/2359
            this.local = socket.localAddress();
            this.remote = socket.remoteAddress();
        }
    }

    public boolean isOpen() {
        return socket.isOpen();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public FileDescriptor fd() {
        return socket;
    }

    @Override
    protected abstract AbstractUringUnsafe newUnsafe();

    @Override
    protected boolean isCompatible(final EventLoop loop) {
        return loop instanceof IOUringEventLoop;
    }

    public void doReadBytes(ByteBuf byteBuf) {
        IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
        IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();

        unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());

        if (byteBuf.hasMemoryAddress()) {
            long eventId = ioUringEventLoop.incrementEventIdCounter();
            final Event event = new Event();
            event.setId(eventId);
            event.setOp(EventType.READ);
            event.setReadBuffer(byteBuf);
            event.setAbstractIOUringChannel(this);
            submissionQueue.add(eventId, EventType.READ, socket.intValue(), byteBuf.memoryAddress(),
                                byteBuf.writerIndex(), byteBuf.capacity());
            ioUringEventLoop.addNewEvent(event);
            submissionQueue.submit();
        }
    }

    protected final ByteBuf newDirectBuffer(ByteBuf buf) {
        return newDirectBuffer(buf, buf);
    }

    protected final ByteBuf newDirectBuffer(Object holder, ByteBuf buf) {
        final int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            ReferenceCountUtil.release(holder);
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

    @Override
    protected void doDisconnect() throws Exception {
    }

    @Override
    protected void doClose() throws Exception {
        System.out.println("DoClose Socket: " + socket.intValue());
        socket.close();
    }

    //deregister
    // Channel/ChannelHandlerContext.read() was called
    @Override
    protected void doBeginRead() {
        final AbstractUringUnsafe unsafe = (AbstractUringUnsafe) unsafe();
        if (!uringInReadyPending) {
            uringInReadyPending = true;
            unsafe.executeUringReadOperator();
        }
    }

    public void executeReadEvent() {
        final AbstractUringUnsafe unsafe = (AbstractUringUnsafe) unsafe();
        unsafe.executeUringReadOperator();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        if (in.size() >= 1) {
            Object msg = in.current();
            if (msg instanceof ByteBuf) {
                doWriteBytes((ByteBuf) msg);
            }
        }
    }

    protected final void doWriteBytes(ByteBuf buf) {
        if (buf.hasMemoryAddress()) {
            IOUringEventLoop ioUringEventLoop = (IOUringEventLoop) eventLoop();
            IOUringSubmissionQueue submissionQueue = ioUringEventLoop.getRingBuffer().getIoUringSubmissionQueue();
            final Event event = new Event();
            long eventId = ioUringEventLoop.incrementEventIdCounter();
            event.setId(eventId);
            event.setOp(EventType.WRITE);
            event.setAbstractIOUringChannel(this);
            submissionQueue.add(eventId, EventType.WRITE, socket.intValue(), buf.memoryAddress(), buf.readerIndex(),
                                buf.writerIndex());
            ioUringEventLoop.addNewEvent(event);
            submissionQueue.submit();
        }
    }

    abstract class AbstractUringUnsafe extends AbstractUnsafe {
        private IOUringRecvByteAllocatorHandle allocHandle;
        private final Runnable readRunnable = new Runnable() {

            @Override
            public void run() {
                uringEventExecution();
            }
        };

        IOUringRecvByteAllocatorHandle newIOUringHandle(RecvByteBufAllocator.ExtendedHandle handle) {
            return new IOUringRecvByteAllocatorHandle(handle);
        }

        @Override
        public IOUringRecvByteAllocatorHandle recvBufAllocHandle() {
            if (allocHandle == null) {
                allocHandle = newIOUringHandle((RecvByteBufAllocator.ExtendedHandle) super.recvBufAllocHandle());
            }
            return allocHandle;
        }

        @Override
        public void connect(final SocketAddress remoteAddress, final SocketAddress localAddress,
                            final ChannelPromise promise) {
            promise.setFailure(new UnsupportedOperationException());
        }

        final void executeUringReadOperator() {
            if (!isActive()) {
                return;
            }
            eventLoop().execute(readRunnable);
        }

        public abstract void uringEventExecution();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        throw new UnsupportedOperationException("unsupported message type");
    }

    @Override
    protected void doRegister() throws Exception {
        ((IOUringEventLoop) eventLoop()).add(this);
    }

    @Override
    protected void doDeregister() throws Exception {
        ((IOUringEventLoop) eventLoop()).remove(this);
    }

    @Override
    public void doBind(final SocketAddress local) throws Exception {
        if (local instanceof InetSocketAddress) {
            checkResolvable((InetSocketAddress) local);
        }
        socket.bind(local);
        this.local = socket.localAddress();
    }

    protected static void checkResolvable(InetSocketAddress addr) {
        if (addr.isUnresolved()) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    public abstract DefaultChannelConfig config();

    @Override
    protected SocketAddress localAddress0() {
        return local;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    public Socket getSocket() {
        return socket;
    }
}
