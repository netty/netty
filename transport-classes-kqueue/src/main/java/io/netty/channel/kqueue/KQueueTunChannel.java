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
package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.DatagramChannel;
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
import java.net.PortUnreachableException;
import java.net.SocketAddress;

import static io.netty.channel.kqueue.BsdSocket.newSocketTun;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * {@link DatagramChannel} implementation that uses linux kqueue edge-triggered mode for
 * maximal performance.
 */
public class KQueueTunChannel extends AbstractKQueueMessageChannel implements TunChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(TunPacket.class) + ", " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';
    static final int AF_INET = 2; // sys/socket.h
    static final int AF_INET6 = 30; // sys/socket.h
    static final int AF_HEADER_LENGTH = 4; // int32
    private final KQueueTunChannelConfig config;

    public KQueueTunChannel() {
        super(null, newSocketTun(), false);
        this.config = new KQueueTunChannelConfig(this);
    }

    @Override
    protected boolean doWriteMessage(final Object msg) throws Exception {
        ByteBuf data;
        int addressFamily;
        if (msg instanceof Tun4Packet) {
            TunPacket packet = (Tun4Packet) msg;
            data = packet.content();
            addressFamily = AF_INET;
        } else if (msg instanceof Tun6Packet) {
            TunPacket packet = (Tun6Packet) msg;
            data = packet.content();
            addressFamily = AF_INET6;
        } else {
            data = (ByteBuf) msg;
            addressFamily = data.getUnsignedByte(0) >> 4 == 4 ? AF_INET : AF_INET6;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        // add address family header
        ByteBuf familyHeader = alloc().directBuffer(AF_HEADER_LENGTH).writeInt(addressFamily);
        data = alloc().compositeDirectBuffer(2).addComponents(true, familyHeader, data.retain());

        try {
            IovArray array = ((KQueueEventLoop) eventLoop()).cleanArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            final long writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
            return writtenBytes > 0;
        } finally {
            data.release();
        }
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
    public KQueueChannelConfig config() {
        return config;
    }

    @Override
    public TunAddress localAddress() {
        return (TunAddress) super.localAddress();
    }

    @Override
    public int mtu() throws IOException {
        return socket.getMtu(((TunAddress) this.local).ifName());
    }

    @Override
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueTunChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        socket.bindTun(local);
        this.local = socket.localAddressTun();
        active = true;

        final int mtu = config.getOption(TUN_MTU);
        if (mtu > 0) {
            socket.setMtu(((TunAddress) this.local).ifName(), mtu);
        }
    }

    final class KQueueTunChannelUnsafe extends AbstractKQueueUnsafe {
        @Override
        void readReady(final KQueueRecvByteAllocatorHandle allocHandle) {
            assert eventLoop().inEventLoop();
            final KQueueChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);
            readReadyBefore();

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

                        final int addressFamily = byteBuf.readInt();

                        // remove address family header
                        byteBuf = byteBuf.slice(AF_HEADER_LENGTH, byteBuf.capacity() - AF_HEADER_LENGTH)
                                .writerIndex(byteBuf.readableBytes());

                        if (addressFamily == AF_INET) {
                            packet = new Tun4Packet(byteBuf);
                        } else if (addressFamily == AF_INET6) {
                            packet = new Tun6Packet(byteBuf);
                        } else {
                            throw new IOException("Unknown internet protocol: " + addressFamily);
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
                readReadyFinally(config);
            }
        }
    }
}
