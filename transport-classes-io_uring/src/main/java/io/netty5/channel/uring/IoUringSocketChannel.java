/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.buffer.Buffer;
import io.netty5.channel.AbstractChannel;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.ReadHandleFactory;
import io.netty5.channel.WriteHandleFactory;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.SocketChannelWriteHandleFactory;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;

import static io.netty5.channel.unix.Limits.IOV_MAX;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
import static java.util.Objects.requireNonNull;

public final class IoUringSocketChannel extends AbstractIoUringChannel<IoUringServerSocketChannel>
        implements SocketChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(IoUringDatagramChannel.class);
    private static final short IS_WRITE = 0;
    private static final short IS_CONNECT = 1;

    private final IovArray writeIovs;
    private final ObjectRing<Promise<Void>> writePromises;

    private Buffer connectInitalData;
    private MsgHdrMemory connectMsgHdr;
    private boolean writeInFlight;
    private boolean moreWritesPending;

    public IoUringSocketChannel(EventLoop eventLoop) {
        this(null, eventLoop, true, new AdaptiveReadHandleFactory(),
                new SocketChannelWriteHandleFactory(Integer.MAX_VALUE, SSIZE_MAX),
                LinuxSocket.newSocketStream(), null, false);
    }

    IoUringSocketChannel(
            IoUringServerSocketChannel parent, EventLoop eventLoop, boolean supportingDisconnect,
            ReadHandleFactory defaultReadHandleFactory, WriteHandleFactory defaultWriteHandleFactory,
            LinuxSocket socket, SocketAddress remote, boolean active) {
        super(parent, eventLoop, supportingDisconnect, defaultReadHandleFactory, defaultWriteHandleFactory,
                socket, remote, active);
        writeIovs = new IovArray();
        writePromises = new ObjectRing<>();
    }

    @Override
    protected boolean processRead(ReadSink readSink, Object read) {
        Buffer buffer = (Buffer) read;
        if (buffer.readableBytes() == 0) {
            // Reading zero bytes means we got EOF, and we should close the channel.
            buffer.close();
            return true;
        }
        readSink.processRead(buffer.capacity(), buffer.readableBytes(), buffer);
        return false;
    }

    @Override
    protected void submitConnect(InetSocketAddress remoteAddress, Buffer initialData) throws IOException {
        if (initialData != null && initialData.isDirect() && supportsTcpFastOpen()) {
            assert writePromises.isEmpty();
            connectInitalData = initialData;
            connectMsgHdr = new MsgHdrMemory();
            try (var itr = initialData.forEachComponent()) {
                var cmp = itr.firstReadable();
                connectMsgHdr.write(socket, remoteAddress, cmp.readableNativeAddress(), cmp.readableBytes(), (short) 0);
            }
            IoUringIoRegistration registration = registration();
            IoUringIoOps ops = IoUringIoOps.newSendmsg(fd().intValue(), 0, Native.MSG_FASTOPEN,
                    connectMsgHdr.address(), IS_CONNECT);
            registration.submit(ops);
            writeInFlight = true;
            return;
        }
        super.submitConnect(remoteAddress, initialData);
    }

    private boolean supportsTcpFastOpen() throws IOException {
        return Native.IS_SUPPORTING_TCP_FASTOPEN_CLIENT &&
                socket.protocolFamily() != SocketProtocolFamily.UNIX &&
                socket.isTcpFastOpenConnect();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        // todo file descriptors???
//        if (socket.protocolFamily() == SocketProtocolFamily.UNIX && msg instanceof FileDescriptor) {
//            return msg;
//        }
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                return intoDirectBuffer(buf, true);
            }
            return buf;
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        if (msg instanceof RegionWriter) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg));
    }

    @Override
    protected void submitAllWriteMessages(WriteSink writeSink) {
        // We need to submit all messages as a single IO, in order to ensure ordering.
        // If we already have an outstanding write promise, we can't write anymore until it completes.
        if (!writeInFlight) {
            writeSink.consumeEachFlushedMessage(this::submitWriteMessage);
            IoUringIoRegistration registration = registration();
            IoUringIoOps ops = IoUringIoOps.newWritev(fd().intValue(), 0, 0, writeIovs.memoryAddress(0),
                    writeIovs.count(), IS_WRITE);
            registration.submit(ops);
            writeInFlight = true;
        }
    }

    private boolean submitWriteMessage(Object msg, Promise<Void> promise) {
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            if (buf.readableBytes() + writeIovs.size() < writeIovs.maxBytes() &&
                    buf.countReadableComponents() + writeIovs.count() < IOV_MAX) {
                writePromises.push(promise, buf.readableBytes());
                writeIovs.addReadable(buf);
            } else {
                return false;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            RegionWriter writer = new RegionWriter(region, promise);
            writer.enqueueWrites();
        } else if (msg instanceof RegionWriter) {
            // Continuation of previous file region.
            RegionWriter writer = (RegionWriter) msg;
            writer.enqueueWrites();
        } else {
            // Should never reach here
            throw new AssertionError("Unrecognized message: " + msg);
        }
        promise.asFuture().addListener(f -> updateWritabilityIfNeeded(true, true));
        return true;
    }

    @Override
    void writeComplete(int result, long udata) {
        writeInFlight = false;
        short data = UserData.decodeData(udata);
        if (data == IS_CONNECT) {
            assert connectInitalData != null;
            if (result > 0) {
                connectInitalData.skipReadableBytes(result);
            }
            connectComplete(0, (short) 0);
            connectInitalData = null;
            connectMsgHdr.release();
            connectMsgHdr = null;
            return;
        }
        assert data == IS_WRITE;
        if (result < 0) {
            var e = Errors.newIOException("write/flush", result);
            writeIovs.clear();
            while (writePromises.poll()) {
                writePromises.getPolledObject().setFailure(e);
            }
            handleWriteError(e);
        } else {
            int leftover = result;
            while (leftover > 0 || writePromises.hasNextStamp(0)) {
                writePromises.peek();
                int size = (int) writePromises.getPolledStamp();
                if (size <= leftover) {
                    writePromises.getPolledObject().setSuccess(null);
                    writePromises.poll();
                    leftover -= size;
                } else {
                    writePromises.updatePeekedStamp(size - leftover);
                    leftover = 0;
                }
            }
            boolean completedAll = writeIovs.completeBytes(result);
            if (moreWritesPending) {
                moreWritesPending = false;
                writeFlushedNow();
            } else if (!completedAll) {
                // We did not write everything. Submit another write IO for the remainder.
                IoUringIoRegistration registration = registration();
                IoUringIoOps ops = IoUringIoOps.newWritev(fd().intValue(), 0, 0, writeIovs.memoryAddress(0),
                        writeIovs.count(), IS_WRITE);
                registration.submit(ops);

                writeInFlight = true;
            }
        }
    }

    @Override
    protected void writeLoopComplete(boolean allWritten) {
        // Don't schedule new write tasks automatically
        if (!allWritten) {
            moreWritesPending = true;
        }
    }

    @Override
    protected boolean isWriteFlushedScheduled() {
        // Avoid queueing new write IOs if we already have a write IO scheduled.
        // We only do one write at a time, because on TCP we have to do the writes in-order,
        // and operations in io_uring can complete out-of-order.
        moreWritesPending = true;
        return !writePromises.isEmpty();
    }

    @Override
    protected ChannelPipeline newChannelPipeline() {
        return new IoUringSocketPipeline(this);
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) throws Exception {
        requireNonNull(direction, "direction");
        try {
            switch (direction) {
                case Inbound:
                    socket.shutdown(true, false);
                    break;
                case Outbound:
                    socket.shutdown(false, true);
                    break;
                default:
                    throw new AssertionError();
            }
        } catch (NotYetConnectedException ignore) {
            // We attempted to shutdown and failed, which means the input has already effectively been
            // shutdown.
        }
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        requireNonNull(direction, "direction");
        switch (direction) {
            case Inbound:
                return socket.isInputShutdown();
            case Outbound:
                return socket.isOutputShutdown();
            default:
                throw new AssertionError("unhandled direction: " + direction);
        }
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }

    @Override
    protected void doClose() {
        try {
            super.doClose();
        } finally {
            writeIovs.release();
        }
    }

    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            try {
                return (T) Boolean.valueOf(socket.isTcpFastOpenConnect());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
            try {
                socket.setTcpFastOpenConnect((Boolean) value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        return option == ChannelOption.TCP_FASTOPEN_CONNECT || super.isExtendedOptionSupported(option);
    }

    private final class RegionWriter implements WritableByteChannel, FutureListener<Void> {
        private final FileRegion region;
        private final Promise<Void> promise;
        private long position;

        RegionWriter(FileRegion region, Promise<Void> promise) {
            this.region = region;
            this.position = region.position();
            this.promise = promise;
        }

        public void enqueueWrites() {
            try {
                region.transferTo(this, position);
            } catch (IOException e) {
                promise.setFailure(e);
            }
        }

        @Override
        public void operationComplete(Future<? extends Void> future) throws Exception {
            if (future.isSuccess()) {
                if (region.count() == region.transferred()) {
                    region.release();
                    promise.setSuccess(null);
                } else {
                    // Enqueue some more writes
                    IoUringSocketPipeline pipeline = (IoUringSocketPipeline) pipeline();
                    pipeline.writeTransportDirect(this, promise); // Do this to reuse the promise.
                    executor().execute(pipeline::flush); // Schedule a flush to get the continuation going ASAP.
                }
            } else {
                promise.tryFailure(new IOException("Write failures on channel", future.cause()));
            }
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (src.remaining() + writeIovs.size() < writeIovs.maxBytes() && writeIovs.count() + 1 < IOV_MAX) {
                Promise<Void> promise = newPromise();
                Buffer buffer = ioBufferAllocator().copyOf(src);
                promise.asFuture().addListener(buffer, CLOSE_BUFFER);
                promise.asFuture().addListener(this);
                writePromises.push(promise, buffer.readableBytes());
                writeIovs.addReadable(buffer);
                position += buffer.readableBytes();
                return buffer.readableBytes();
            }
            return 0;
        }

        @Override
        public boolean isOpen() {
            return IoUringSocketChannel.this.isOpen();
        }

        @Override
        public void close() throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    private static final class IoUringSocketPipeline extends DefaultAbstractChannelPipeline {
        IoUringSocketPipeline(AbstractChannel<?, ?, ?> channel) {
            super(channel);
        }

        void writeTransportDirect(Object msg, Promise<Void> promise) {
            writeTransport(msg, promise);
        }
    }
}
