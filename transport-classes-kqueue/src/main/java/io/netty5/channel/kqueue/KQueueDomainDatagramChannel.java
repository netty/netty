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
package io.netty5.channel.kqueue;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.ByteBufConvertible;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.DefaultByteBufAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.unix.BufferDomainDatagramPacket;
import io.netty5.channel.unix.DomainDatagramChannel;
import io.netty5.channel.unix.DomainDatagramChannelConfig;
import io.netty5.channel.unix.DomainDatagramPacket;
import io.netty5.channel.unix.DomainDatagramSocketAddress;
import io.netty5.channel.unix.DomainSocketAddress;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.PeerCredentials;
import io.netty5.channel.unix.RecvFromAddressDomainSocket;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty5.channel.kqueue.BsdSocket.newSocketDomainDgram;
import static io.netty5.util.CharsetUtil.UTF_8;

@UnstableApi
public final class KQueueDomainDatagramChannel extends AbstractKQueueDatagramChannel implements DomainDatagramChannel {

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

    private final KQueueDomainDatagramChannelConfig config;

    public KQueueDomainDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, newSocketDomainDgram(), false);
    }

    public KQueueDomainDatagramChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new BsdSocket(fd), true);
    }

    private KQueueDomainDatagramChannel(EventLoop eventLoop, BsdSocket socket, boolean active) {
        super(null, eventLoop, socket, active);
        config = new KQueueDomainDatagramChannelConfig(this);
    }

    @Override
    public KQueueDomainDatagramChannelConfig config() {
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
    protected boolean doWriteMessage(Object msg) throws Exception {
        final Object data;
        DomainSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, DomainSocketAddress> envelope =
                    (AddressedEnvelope<?, DomainSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = msg;
            remoteAddress = null;
        }

        if (data instanceof Buffer) {
            return doWriteBufferMessage((Buffer) data, remoteAddress);
        }
        return doWriteByteBufMessage((ByteBuf) data, remoteAddress);
    }

    private boolean doWriteBufferMessage(Buffer data, DomainSocketAddress remoteAddress) throws IOException {
        final int initialReadableBytes = data.readableBytes();
        if (initialReadableBytes == 0) {
            return true;
        }

        if (data.countReadableComponents() > 1) {
            IovArray array = registration().cleanArray();
            data.forEachReadable(0, array);
            int count = array.count();
            assert count != 0;

            final long writtenBytes;
            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), count);
            } else {
                writtenBytes = socket.sendToAddressesDomainSocket(
                        array.memoryAddress(0), count, remoteAddress.path().getBytes(UTF_8));
            }
            return writtenBytes > 0;
        } else {
            if (remoteAddress == null) {
                data.forEachReadable(0, (index, component) -> {
                    int written = socket.writeAddress(component.readableNativeAddress(), 0, component.readableBytes());
                    component.skipReadable(written);
                    return false;
                });
            } else {
                data.forEachReadable(0, (index, component) -> {
                    int written = socket.sendToAddressDomainSocket(
                            component.readableNativeAddress(), 0, component.readableBytes(),
                            remoteAddress.path().getBytes(UTF_8));
                    component.skipReadable(written);
                    return false;
                });
            }
            return data.readableBytes() < initialReadableBytes;
        }
    }

    private boolean doWriteByteBufMessage(ByteBuf data, DomainSocketAddress remoteAddress) throws IOException {
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
                                                                remoteAddress.path().getBytes(UTF_8));
            }
        } else if (data.nioBufferCount() > 1) {
            IovArray array = registration().cleanArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
            } else {
                writtenBytes = socket.sendToAddressesDomainSocket(array.memoryAddress(0), cnt,
                                                                  remoteAddress.path().getBytes(UTF_8));
            }
        } else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            if (remoteAddress == null) {
                writtenBytes = socket.write(nioData, nioData.position(), nioData.limit());
            } else {
                writtenBytes = socket.sendToDomainSocket(nioData, nioData.position(), nioData.limit(),
                                                         remoteAddress.path().getBytes(UTF_8));
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
        if (msg instanceof BufferDomainDatagramPacket) {
            BufferDomainDatagramPacket packet = (BufferDomainDatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content)?
                    new BufferDomainDatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }
        if (msg instanceof ByteBufConvertible) {
            ByteBuf buf = ((ByteBufConvertible) msg).asByteBuf();
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            SocketAddress recipient = e.recipient();
            if (recipient == null || recipient instanceof DomainSocketAddress) {
                DomainSocketAddress domainRecipient = (DomainSocketAddress) recipient;
                if (e.content() instanceof Buffer) {
                    Buffer buf = (Buffer) e.content();
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(newDirectBuffer(buf), domainRecipient);
                        } finally {
                            Resource.dispose(e);
                        }
                    }
                    return e;
                }
                if (e.content() instanceof ByteBufConvertible) {
                    ByteBuf content = ((ByteBufConvertible) e.content()).asByteBuf();
                    return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                            new DefaultByteBufAddressedEnvelope<>(newDirectBuffer(e, content), domainRecipient) : e;
                }
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
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueDomainDatagramChannelUnsafe();
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

    final class KQueueDomainDatagramChannelUnsafe extends AbstractKQueueUnsafe {

        @Override
        void readReady(KQueueRecvBufferAllocatorHandle allocHandle) {
            assert executor().inEventLoop();
            final DomainDatagramChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final boolean useBufferApi = config.getRecvBufferAllocatorUseBuffer();
            allocHandle.reset(config);
            readReadyBefore();

            try {
                Throwable exception = useBufferApi ?
                        doReadBuffer(allocHandle, pipeline) :
                        doReadByteBuf(allocHandle, pipeline);

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                } else {
                    readIfIsAutoRead();
                }
            } finally {
                readReadyFinally(config);
            }
        }

        private Throwable doReadBuffer(KQueueRecvBufferAllocatorHandle allocHandle, ChannelPipeline pipeline) {
            BufferAllocator allocator = config().getBufferAllocator();
            if (allocator.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
                allocator = DefaultBufferAllocators.offHeapAllocator();
            }
            Buffer buf = null;
            try {
                boolean connected = isConnected();
                do {
                    buf = allocHandle.allocate(allocator);
                    allocHandle.attemptedBytesRead(buf.writableBytes());

                    final BufferDomainDatagramPacket packet;
                    if (connected) {
                        doReadBytes(buf);
                        if (allocHandle.lastBytesRead() <= 0) {
                            // nothing was read, release the buffer.
                            buf.close();
                            break;
                        }
                        packet = new BufferDomainDatagramPacket(buf, (DomainSocketAddress) localAddress(),
                                                          (DomainSocketAddress) remoteAddress());
                    } else {
                        final RecvFromAddressDomainSocket recvFrom = new RecvFromAddressDomainSocket(socket);
                        buf.forEachWritable(0, recvFrom);
                        final DomainDatagramSocketAddress remoteAddress = recvFrom.remoteAddress();

                        if (remoteAddress == null) {
                            allocHandle.lastBytesRead(-1);
                            buf.close();
                            break;
                        }
                        DomainSocketAddress localAddress = remoteAddress.localAddress();
                        if (localAddress == null) {
                            localAddress = (DomainSocketAddress) localAddress();
                        }
                        allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                        buf.skipWritable(allocHandle.lastBytesRead());

                        packet = new BufferDomainDatagramPacket(buf, localAddress, remoteAddress);
                    }

                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(packet);

                    buf = null;

                    // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                    // as we read anything).
                } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
            } catch (Throwable t) {
                if (buf != null) {
                    buf.close();
                }
                return t;
            }
            return null;
        }

        private Throwable doReadByteBuf(KQueueRecvBufferAllocatorHandle allocHandle, ChannelPipeline pipeline) {
            ByteBufAllocator allocator = config().getAllocator();
            ByteBuf buf = null;
            try {
                boolean connected = isConnected();
                do {
                    buf = allocHandle.allocate(allocator);
                    allocHandle.attemptedBytesRead(buf.writableBytes());

                    final DomainDatagramPacket packet;
                    if (connected) {
                        allocHandle.lastBytesRead(doReadBytes(buf));
                        if (allocHandle.lastBytesRead() <= 0) {
                            // nothing was read, release the buffer.
                            buf.release();
                            break;
                        }
                        packet = new DomainDatagramPacket(buf, (DomainSocketAddress) localAddress(),
                                (DomainSocketAddress) remoteAddress());
                    } else {
                        final DomainDatagramSocketAddress remoteAddress;
                        if (buf.hasMemoryAddress()) {
                            // has a memory address so use optimized call
                            remoteAddress = socket.recvFromAddressDomainSocket(buf.memoryAddress(),
                                    buf.writerIndex(), buf.capacity());
                        } else {
                            ByteBuffer nioData = buf.internalNioBuffer(
                                    buf.writerIndex(), buf.writableBytes());
                            remoteAddress =
                                    socket.recvFromDomainSocket(nioData, nioData.position(), nioData.limit());
                        }

                        if (remoteAddress == null) {
                            allocHandle.lastBytesRead(-1);
                            buf.release();
                            break;
                        }
                        DomainSocketAddress localAddress = remoteAddress.localAddress();
                        if (localAddress == null) {
                            localAddress = (DomainSocketAddress) localAddress();
                        }
                        allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                        buf.writerIndex(buf.writerIndex() + allocHandle.lastBytesRead());

                        packet = new DomainDatagramPacket(buf, localAddress, remoteAddress);
                    }

                    allocHandle.incMessagesRead(1);

                    readPending = false;
                    pipeline.fireChannelRead(packet);

                    buf = null;

                    // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                    // as we read anything).
                } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
            } catch (Throwable t) {
                if (buf != null) {
                    buf.release();
                }
                return t;
            }
            return null;
        }
    }
}
