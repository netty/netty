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
package io.netty5.channel.kqueue;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramChannelConfig;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.channel.unix.DatagramSocketAddress;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

import static io.netty5.channel.kqueue.BsdSocket.newSocketDgram;
import static java.util.Objects.requireNonNull;

@UnstableApi
public final class KQueueDatagramChannel extends AbstractKQueueDatagramChannel implements DatagramChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private volatile boolean connected;
    private final KQueueDatagramChannelConfig config;

    public KQueueDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, InternetProtocolFamily protocolFamily) {
        super(null, eventLoop, newSocketDgram(protocolFamily), false);
        config = new KQueueDatagramChannelConfig(this);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, int fd) {
        this(eventLoop, new BsdSocket(fd), true);
    }

    KQueueDatagramChannel(EventLoop eventLoop, BsdSocket socket, boolean active) {
        super(null, eventLoop, socket, active);
        config = new KQueueDatagramChannelConfig(this);
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
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
    public Future<Void> joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress, Promise<Void> promise) {
        try {
            NetworkInterface iface = config().getNetworkInterface();
            if (iface == null) {
                iface = NetworkInterface.getByInetAddress(localAddress().getAddress());
            }
            return joinGroup(multicastAddress, iface, null, promise);
        } catch (SocketException e) {
            return promise.setFailure(e).asFuture();
        }
    }

    @Override
    public Future<Void> joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public Future<Void> joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            Promise<Void> promise) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public Future<Void> joinGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise.asFuture();
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress, Promise<Void> promise) {
        try {
            return leaveGroup(
                    multicastAddress, NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (SocketException e) {
            return promise.setFailure(e).asFuture();
        }
    }

    @Override
    public Future<Void> leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(
            InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, Promise<Void> promise) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public Future<Void> leaveGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise.asFuture();
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public Future<Void> block(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress sourceToBlock, final Promise<Void> promise) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");
        requireNonNull(networkInterface, "networkInterface");
        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise.asFuture();
    }

    @Override
    public Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, InetAddress sourceToBlock, Promise<Void> promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (Throwable e) {
            return promise.setFailure(e).asFuture();
        }
    }

    @Override
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueDatagramChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected boolean doWriteMessage(Object msg) throws Exception {
        final Object data;
        final InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<?, InetSocketAddress> envelope = (AddressedEnvelope<?, InetSocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = msg;
            remoteAddress = null;
        }

        return doWriteBufferMessage((Buffer) data, remoteAddress);
    }

    private boolean doWriteBufferMessage(Buffer data, InetSocketAddress remoteAddress) throws IOException {
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
                writtenBytes = socket.sendToAddresses(array.memoryAddress(0), count,
                                                      remoteAddress.getAddress(), remoteAddress.getPort());
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
                    int written = socket.sendToAddress(component.readableNativeAddress(), 0, component.readableBytes(),
                                                            remoteAddress.getAddress(), remoteAddress.getPort());
                    component.skipReadable(written);
                    return false;
                });
            }
            return data.readableBytes() < initialReadableBytes;
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            Buffer content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content)?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            SocketAddress recipient = e.recipient();
            if (recipient == null || recipient instanceof InetSocketAddress) {
                InetSocketAddress inetRecipient = (InetSocketAddress) recipient;
                if (e.content() instanceof Buffer) {
                    Buffer buf = (Buffer) e.content();
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(newDirectBuffer(buf), inetRecipient);
                        } finally {
                            ReferenceCountUtil.release(e);
                        }
                    }
                    return e;
                }
            }
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public KQueueDatagramChannelConfig config() {
        return config;
    }

    @Override
    protected void doDisconnect() throws Exception {
        socket.disconnect();
        connected = active = false;
        resetCachedAddresses();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (super.doConnect(remoteAddress, localAddress)) {
            connected = true;
            return true;
        }
        return false;
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        connected = false;
    }

    final class KQueueDatagramChannelUnsafe extends AbstractKQueueUnsafe {

        @Override
        void readReady(KQueueRecvBufferAllocatorHandle allocHandle) {
            assert executor().inEventLoop();
            final DatagramChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final BufferAllocator allocator = config.getBufferAllocator();
            allocHandle.reset(config);
            readReadyBefore();

            Throwable exception = null;
            try {
                Buffer buffer = null;
                try {
                    boolean connected = isConnected();
                    do {
                        buffer = allocHandle.allocate(allocator);
                        allocHandle.attemptedBytesRead(buffer.writableBytes());

                        final DatagramPacket packet;
                        if (connected) {
                            try {
                                allocHandle.lastBytesRead(doReadBytes(buffer));
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
                                buffer.close();
                                buffer = null;
                                break;
                            }
                            packet = new DatagramPacket(buffer,
                                    (InetSocketAddress) localAddress(), (InetSocketAddress) remoteAddress());
                        } else {
                            final DatagramSocketAddress remoteAddress;
                            try (var iterator = buffer.forEachWritable()) {
                                var component = iterator.first();
                                long addr = component.writableNativeAddress();
                                if (addr != 0) {
                                    // has a memory address so use optimized call
                                    remoteAddress = socket.recvFromAddress(addr, 0, component.writableBytes());
                                } else {
                                    ByteBuffer nioData = component.writableBuffer();
                                    remoteAddress = socket.recvFrom(nioData, nioData.position(), nioData.limit());
                                }
                            }

                            if (remoteAddress == null) {
                                allocHandle.lastBytesRead(-1);
                                buffer.close();
                                buffer = null;
                                break;
                            }
                            InetSocketAddress localAddress = remoteAddress.localAddress();
                            if (localAddress == null) {
                                localAddress = (InetSocketAddress) localAddress();
                            }
                            allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                            buffer.skipWritable(allocHandle.lastBytesRead());

                            packet = new DatagramPacket(buffer, localAddress, remoteAddress);
                        }

                        allocHandle.incMessagesRead(1);

                        readPending = false;
                        pipeline.fireChannelRead(packet);

                        buffer = null;

                    // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                    // as we read anything).
                    } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
                } catch (Throwable t) {
                    if (buffer != null) {
                        buffer.close();
                    }
                    exception = t;
                }

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
    }
}
