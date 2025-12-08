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
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.Tun6Packet;
import io.netty.channel.socket.TunAddress;
import io.netty.channel.socket.TunChannel;
import io.netty.channel.socket.TunPacket;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.epoll.EpollTunChannelOption.IFF_MULTI_QUEUE;
import static io.netty.channel.epoll.LinuxSocket.newSocketTun;
import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * {@link TunChannel} implementation that uses linux epoll edge-triggered mode for maximal
 * performance.
 */
public class EpollTunChannel extends AbstractEpollChannel implements TunChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(TunPacket.class) + ", " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';
    private final EpollTunChannelConfig config;

    public EpollTunChannel() {
        super(null, newSocketTun(), false);
        this.config = new EpollTunChannelConfig(this);
    }

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     */
    @Override
    protected int doReadBytes(ByteBuf byteBuf) throws Exception {
        int writerIndex = byteBuf.writerIndex();
        int localReadAmount;
        unsafe().recvBufAllocHandle().attemptedBytesRead(byteBuf.writableBytes());
        if (byteBuf.hasMemoryAddress()) {
            localReadAmount = socket.readAddress(byteBuf.memoryAddress(), writerIndex, byteBuf.capacity());
        } else {
            ByteBuffer buf = byteBuf.internalNioBuffer(writerIndex, byteBuf.writableBytes());
            localReadAmount = socket.read(buf, buf.position(), buf.limit());
        }
        if (localReadAmount > 0) {
            byteBuf.writerIndex(writerIndex + localReadAmount);
        }
        return localReadAmount;
    }

    @Override
    protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                break;
            }

            boolean done = false;
            for (int i = config().getWriteSpinCount(); i > 0; --i) {
                if (doWriteMessage(msg)) {
                    done = true;
                    break;
                }
            }

            if (done) {
                in.remove();
                maxMessagesPerWrite--;
            } else {
                break;
            }
        }

        if (in.isEmpty()) {
            // Did write all messages.
            clearFlag(Native.EPOLLOUT);
        } else {
            // Did not write all messages.
            setFlag(Native.EPOLLOUT);
        }
    }

    private boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        if (msg instanceof TunPacket) {
            TunPacket packet = (TunPacket) msg;
            data = packet.content();
        } else {
            data = (ByteBuf) msg;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        return doWriteOrSendBytes(data, null, false) > 0;
    }

    @Override
    protected int doWriteBytes(ChannelOutboundBuffer in, ByteBuf buf) throws Exception {
        if (buf.hasMemoryAddress()) {
            int localFlushedAmount = socket.writeAddress(buf.memoryAddress(), buf.readerIndex(), buf.writerIndex());
            if (localFlushedAmount > 0) {
                in.removeBytes(localFlushedAmount);
                return 1;
            }
        } else {
            final ByteBuffer nioBuf = buf.nioBufferCount() == 1 ?
                    buf.internalNioBuffer(buf.readerIndex(), buf.readableBytes()) : buf.nioBuffer();
            int localFlushedAmount = socket.write(nioBuf, nioBuf.position(), nioBuf.limit());
            if (localFlushedAmount > 0) {
                nioBuf.position(nioBuf.position() + localFlushedAmount);
                in.removeBytes(localFlushedAmount);
                return 1;
            }
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write bytes to the socket, with or without a remote address.
     */
    @Override
    protected long doWriteOrSendBytes(ByteBuf data, InetSocketAddress remoteAddress, boolean fastOpen)
            throws IOException {
        assert !(fastOpen && remoteAddress == null) : "fastOpen requires a remote address";
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            if (remoteAddress == null) {
                return socket.writeAddress(memoryAddress, data.readerIndex(), data.writerIndex());
            }
            return socket.sendToAddress(memoryAddress, data.readerIndex(), data.writerIndex(),
                    remoteAddress.getAddress(), remoteAddress.getPort(), fastOpen);
        }

        if (data.nioBufferCount() > 1) {
            IovArray array = ((EpollEventLoop) eventLoop()).cleanIovArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            if (remoteAddress == null) {
                return socket.writevAddresses(array.memoryAddress(0), cnt);
            }
            return socket.sendToAddresses(array.memoryAddress(0), cnt,
                    remoteAddress.getAddress(), remoteAddress.getPort(), fastOpen);
        }

        ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
        if (remoteAddress == null) {
            return socket.write(nioData, nioData.position(), nioData.limit());
        }
        return socket.sendTo(nioData, nioData.position(), nioData.limit(),
                remoteAddress.getAddress(), remoteAddress.getPort(), fastOpen);
    }

    @Override
    protected Object filterOutboundMessage(final Object msg) {
        if (msg instanceof Tun4Packet) {
            Tun4Packet packet = (Tun4Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new Tun4Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof Tun6Packet) {
            Tun6Packet packet = (Tun6Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new Tun6Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public EpollChannelConfig config() {
        return config;
    }

    @Override
    public TunAddress localAddress() {
        return (TunAddress) super.localAddress();
    }

    @Override
    public int mtu() throws IOException {
        return LinuxSocket.getMtu(localAddress().ifName());
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollTunChannelUnsafe();
    }

    @Override
    protected void doRegister() {
        // skip registration at EpollEventLoop, since TUN device must be bound first
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        // TUN device must be bound before adding to EpollEventLoop
        final boolean multiqueue = config.getOption(IFF_MULTI_QUEUE);
        this.local = socket.bindTun(local, multiqueue);
        super.doRegister();
        active = true;

        final int mtu = config.getOption(TUN_MTU);
        if (mtu > 0) {
            LinuxSocket.setMtu(((TunAddress) this.local).ifName(), mtu);
        }
    }

    final class EpollTunChannelUnsafe extends AbstractEpollUnsafe {
        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            EpollChannelConfig config = config();
            if (shouldBreakEpollInReady(config)) {
                clearEpollIn0();
                return;
            }
            final EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.edgeTriggered(isFlagSet(Native.EPOLLET));

            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);
            epollInBefore();

            Throwable exception = null;
            try {
                ByteBuf byteBuf = null;
                try {
                    do {
                        byteBuf = allocHandle.allocate(allocator);
                        allocHandle.attemptedBytesRead(byteBuf.writableBytes());

                        final TunPacket packet;
                        try {
                            allocHandle.lastBytesRead(doReadBytes(byteBuf));
                        } catch (Errors.NativeIoException e) {
                            // We need to correctly translate connect errors to match NIO behaviour.
                            if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
                                PortUnreachableException error = new PortUnreachableException(e.getMessage());
                                error.initCause(e);
                                throw error;
                            }
                            throw e;
                        }
                        if (allocHandle.lastBytesRead() <= 0) {
                            // nothing was read, release the buffer.
                            byteBuf.release();
                            byteBuf = null;
                            break;
                        }

                        final int version = byteBuf.getUnsignedByte(0) >> 4;
                        if (version == 4) {
                            packet = new Tun4Packet(byteBuf);
                        } else if (version == 6) {
                            packet = new Tun6Packet(byteBuf);
                        } else {
                            throw new IOException("Unknown internet protocol: " + version);
                        }

                        allocHandle.incMessagesRead(1);

                        readPending = false;
                        pipeline.fireChannelRead(packet);

                        byteBuf = null;

                        // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                        // as we read anything).
                    } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
                } catch (Throwable t) {
                    if (byteBuf != null) {
                        byteBuf.release();
                    }
                    exception = t;
                }

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
            } finally {
                epollInFinally(config);
            }
        }
    }
}
