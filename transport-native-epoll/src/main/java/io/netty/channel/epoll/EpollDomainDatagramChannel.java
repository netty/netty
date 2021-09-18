/*
 * Copyright 2021 The Netty Project
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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.unix.DomainDatagramChannel;
import io.netty.channel.unix.DomainDatagramChannelConfig;
import io.netty.channel.unix.DomainDatagramPacket;
import io.netty.channel.unix.DomainDatagramSocketAddress;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.epoll.LinuxSocket.newSocketDomainDgram;

@UnstableApi
public final class EpollDomainDatagramChannel extends AbstractEpollChannel implements DomainDatagramChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(true);

    private static final String EXPECTED_TYPES =
            " (expected: " +
                    StringUtil.simpleClassName(DomainDatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(ByteBuf.class) + ", " +
                    StringUtil.simpleClassName(DomainSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';

    private volatile boolean connected;
    private volatile DomainSocketAddress local;
    private volatile DomainSocketAddress remote;

    private final EpollDomainDatagramChannelConfig config;

    public EpollDomainDatagramChannel() {
        this(newSocketDomainDgram(), false);
    }

    public EpollDomainDatagramChannel(int fd) {
        this(new LinuxSocket(fd), true);
    }

    private EpollDomainDatagramChannel(LinuxSocket socket, boolean active) {
        super(null, socket, active);
        config = new EpollDomainDatagramChannelConfig(this);
    }

    @Override
    public EpollDomainDatagramChannelConfig config() {
        return config;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        local = (DomainSocketAddress) localAddress;
        active = true;
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        connected = active = false;
        local = null;
        remote = null;
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            if (localAddress != null) {
                local = (DomainSocketAddress) localAddress;
            }
            remote = (DomainSocketAddress) remoteAddress;
            connected = true;
            return true;
        }
        return false;
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }

            try {
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
            } catch (IOException e) {
                maxMessagesPerWrite--;

                // Continue on write error as a DatagramChannel can write to multiple remote peers
                //
                // See https://github.com/netty/netty/issues/2665
                in.remove(e);
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
        DomainSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<ByteBuf, DomainSocketAddress> envelope =
                    (AddressedEnvelope<ByteBuf, DomainSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = (ByteBuf) msg;
            remoteAddress = null;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        final long writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            if (remoteAddress == null) {
                writtenBytes = socket.writeAddress(memoryAddress, data.readerIndex(), data.writerIndex());
            } else {
                writtenBytes = socket.sendToAddressDomainSocket(memoryAddress, data.readerIndex(), data.writerIndex(),
                        remoteAddress.path().getBytes(CharsetUtil.UTF_8));
            }
        } else if (data.nioBufferCount() > 1) {
            IovArray array = ((EpollEventLoop) eventLoop()).cleanIovArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
            } else {
                writtenBytes = socket.sendToAddressesDomainSocket(array.memoryAddress(0), cnt,
                        remoteAddress.path().getBytes(CharsetUtil.UTF_8));
            }
        } else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            if (remoteAddress == null) {
                writtenBytes = socket.write(nioData, nioData.position(), nioData.limit());
            } else {
                writtenBytes = socket.sendToDomainSocket(nioData, nioData.position(), nioData.limit(),
                        remoteAddress.path().getBytes(CharsetUtil.UTF_8));
            }
        }

        return writtenBytes > 0;
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DomainDatagramPacket) {
            DomainDatagramPacket packet = (DomainDatagramPacket) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DomainDatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            if (e.content() instanceof ByteBuf &&
                    (e.recipient() == null || e.recipient() instanceof DomainSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                        new DefaultAddressedEnvelope<ByteBuf, DomainSocketAddress>(
                                newDirectBuffer(e, content), (DomainSocketAddress) e.recipient()) : e;
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public DomainSocketAddress localAddress() {
        return (DomainSocketAddress) super.localAddress();
    }

    @Override
    protected DomainSocketAddress localAddress0() {
        return local;
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollDomainDatagramChannelUnsafe();
    }

    /**
     * Returns the unix credentials (uid, gid, pid) of the peer
     * <a href=https://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
     */
    public PeerCredentials peerCredentials() throws IOException {
        return socket.getPeerCredentials();
    }

    @Override
    public DomainSocketAddress remoteAddress() {
        return (DomainSocketAddress) super.remoteAddress();
    }

    @Override
    protected DomainSocketAddress remoteAddress0() {
        return remote;
    }

    final class EpollDomainDatagramChannelUnsafe extends AbstractEpollUnsafe {

        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            final DomainDatagramChannelConfig config = config();
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
                    boolean connected = isConnected();
                    do {
                        byteBuf = allocHandle.allocate(allocator);
                        allocHandle.attemptedBytesRead(byteBuf.writableBytes());

                        final DomainDatagramPacket packet;
                        if (connected) {
                            allocHandle.lastBytesRead(doReadBytes(byteBuf));
                            if (allocHandle.lastBytesRead() <= 0) {
                                // nothing was read, release the buffer.
                                byteBuf.release();
                                break;
                            }
                            packet = new DomainDatagramPacket(byteBuf, (DomainSocketAddress) localAddress(),
                                    (DomainSocketAddress) remoteAddress());
                        } else {
                            final DomainDatagramSocketAddress remoteAddress;
                            if (byteBuf.hasMemoryAddress()) {
                                // has a memory address so use optimized call
                                remoteAddress = socket.recvFromAddressDomainSocket(byteBuf.memoryAddress(),
                                        byteBuf.writerIndex(), byteBuf.capacity());
                            } else {
                                ByteBuffer nioData = byteBuf.internalNioBuffer(
                                        byteBuf.writerIndex(), byteBuf.writableBytes());
                                remoteAddress =
                                        socket.recvFromDomainSocket(nioData, nioData.position(), nioData.limit());
                            }

                            if (remoteAddress == null) {
                                allocHandle.lastBytesRead(-1);
                                byteBuf.release();
                                break;
                            }
                            DomainSocketAddress localAddress = remoteAddress.localAddress();
                            if (localAddress == null) {
                                localAddress = (DomainSocketAddress) localAddress();
                            }
                            allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                            byteBuf.writerIndex(byteBuf.writerIndex() + allocHandle.lastBytesRead());

                            packet = new DomainDatagramPacket(byteBuf, localAddress, remoteAddress);
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
