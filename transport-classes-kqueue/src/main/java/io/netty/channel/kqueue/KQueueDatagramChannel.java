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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.ControlMessageBuffer;
import io.netty.channel.unix.ControlMessageDatagramPacket;
import io.netty.channel.unix.ControlMessageDecoder;
import io.netty.channel.unix.ControlMessageEncoder;
import io.netty.channel.unix.ControlMessages;
import io.netty.channel.unix.DatagramSocketAddress;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;

import static io.netty.channel.kqueue.BsdSocket.newSocketDgram;

public final class KQueueDatagramChannel extends AbstractKQueueDatagramChannel implements DatagramChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(ByteBuf.class) + ", " +
                    StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';

    private volatile boolean connected;
    private final KQueueDatagramChannelConfig config;

    public KQueueDatagramChannel() {
        super(null, newSocketDgram(), false);
        config = new KQueueDatagramChannelConfig(this);
    }

    /**
     * @deprecated use {@link KQueueDatagramChannel#KQueueDatagramChannel(SocketProtocolFamily)}
     */
    @Deprecated
    public KQueueDatagramChannel(InternetProtocolFamily protocol) {
        super(null, newSocketDgram(protocol), false);
        config = new KQueueDatagramChannelConfig(this);
    }

    public KQueueDatagramChannel(SocketProtocolFamily protocol) {
        super(null, newSocketDgram(protocol), false);
        config = new KQueueDatagramChannelConfig(this);
    }

    public KQueueDatagramChannel(int fd) {
        this(new BsdSocket(fd), true);
    }

    KQueueDatagramChannel(BsdSocket socket, boolean active) {
        super(null, socket, active);
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
    @SuppressWarnings("deprecation")
    public boolean isActive() {
        return socket.isOpen() && (config.getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress) {
        return joinGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            NetworkInterface iface = config().getNetworkInterface();
            if (iface == null) {
                iface = NetworkInterface.getByInetAddress(localAddress().getAddress());
            }
            return joinGroup(multicastAddress, iface, null, promise);
        } catch (SocketException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return joinGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface,
            ChannelPromise promise) {
        return joinGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public ChannelFuture joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return joinGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture joinGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress source, final ChannelPromise promise) {

        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise;
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress) {
        return leaveGroup(multicastAddress, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(InetAddress multicastAddress, ChannelPromise promise) {
        try {
            return leaveGroup(
                    multicastAddress, NetworkInterface.getByInetAddress(localAddress().getAddress()), null, promise);
        } catch (SocketException e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        return leaveGroup(multicastAddress, networkInterface, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(
            InetSocketAddress multicastAddress,
            NetworkInterface networkInterface, ChannelPromise promise) {
        return leaveGroup(multicastAddress.getAddress(), networkInterface, null, promise);
    }

    @Override
    public ChannelFuture leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        return leaveGroup(multicastAddress, networkInterface, source, newPromise());
    }

    @Override
    public ChannelFuture leaveGroup(
            final InetAddress multicastAddress, final NetworkInterface networkInterface, final InetAddress source,
            final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");

        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));

        return promise;
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        return block(multicastAddress, networkInterface, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            final InetAddress multicastAddress, final NetworkInterface networkInterface,
            final InetAddress sourceToBlock, final ChannelPromise promise) {
        ObjectUtil.checkNotNull(multicastAddress, "multicastAddress");
        ObjectUtil.checkNotNull(sourceToBlock, "sourceToBlock");
        ObjectUtil.checkNotNull(networkInterface, "networkInterface");
        promise.setFailure(new UnsupportedOperationException("Multicast not supported"));
        return promise;
    }

    @Override
    public ChannelFuture block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        return block(multicastAddress, sourceToBlock, newPromise());
    }

    @Override
    public ChannelFuture block(
            InetAddress multicastAddress, InetAddress sourceToBlock, ChannelPromise promise) {
        try {
            return block(
                    multicastAddress,
                    NetworkInterface.getByInetAddress(localAddress().getAddress()),
                    sourceToBlock, promise);
        } catch (Throwable e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueDatagramChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (localAddress instanceof InetSocketAddress) {
            InetSocketAddress socketAddress = (InetSocketAddress) localAddress;
            if (socketAddress.getAddress().isAnyLocalAddress() &&
                    socketAddress.getAddress() instanceof Inet4Address) {
                if (socket.family() == SocketProtocolFamily.INET6) {
                    localAddress = new InetSocketAddress(Native.INET6_ANY, socketAddress.getPort());
                }
            }
        }
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        InetSocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<ByteBuf, InetSocketAddress> envelope =
                    (AddressedEnvelope<ByteBuf, InetSocketAddress>) msg;
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

        if (msg instanceof ControlMessageDatagramPacket) {
            ControlMessages controlMessages = ((ControlMessageDatagramPacket) msg).controlMessages();
            if (!controlMessages.isEmpty()) {
                return doWriteMessageWithControlMessages(data, remoteAddress, controlMessages);
            }
        }

        final long writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            if (remoteAddress == null) {
                try {
                    writtenBytes = socket.writeAddress(memoryAddress, data.readerIndex(), data.writerIndex());
                } catch (Errors.NativeIoException e) {
                    throw translateForConnected(e);
                }
            } else {
                writtenBytes = socket.sendToAddress(memoryAddress, data.readerIndex(), data.writerIndex(),
                        remoteAddress.getAddress(), remoteAddress.getPort());
            }
        } else if (data.nioBufferCount() > 1) {
            IovArray array = ((NativeArrays) registration().attachment()).cleanIovArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            if (remoteAddress == null) {
                writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
            } else {
                writtenBytes = socket.sendToAddresses(array.memoryAddress(0), cnt,
                        remoteAddress.getAddress(), remoteAddress.getPort());
            }
        } else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            if (remoteAddress == null) {
                writtenBytes = socket.write(nioData, nioData.position(), nioData.limit());
            } else {
                writtenBytes = socket.sendTo(nioData, nioData.position(), nioData.limit(),
                        remoteAddress.getAddress(), remoteAddress.getPort());
            }
        }

        return writtenBytes > 0;
    }

    private boolean doWriteMessageWithControlMessages(ByteBuf data, InetSocketAddress remoteAddress,
                                                      ControlMessages controlMessages) throws IOException {
        NativeArrays nativeArrays = (NativeArrays) registration().attachment();
        IovArray array = nativeArrays.cleanIovArray();
        array.add(data, data.readerIndex(), data.readableBytes());
        int cnt = array.count();
        assert cnt != 0;

        ControlMessageBuffer controlMessageBuffer = nativeArrays.cleanControlMessageBuffer();
        int controlLength = ControlMessageEncoder.encodedLength(controlMessages);
        int controlOffset = controlMessageBuffer.reserve(controlLength);
        ControlMessageEncoder.encode(controlMessages, controlMessageBuffer.buffer(), controlOffset);

        final long writtenBytes;
        // macOS and the BSDs fail with EISCONN if an address is specified for a connected socket.
        if (remoteAddress == null || connected && remoteAddress.equals(remoteAddress())) {
            writtenBytes = socket.sendToAddresses(array.memoryAddress(0), cnt, null, 0,
                    controlMessageBuffer.memoryAddress(controlOffset), controlLength);
        } else {
            writtenBytes = socket.sendToAddresses(array.memoryAddress(0), cnt,
                    remoteAddress.getAddress(), remoteAddress.getPort(),
                    controlMessageBuffer.memoryAddress(controlOffset), controlLength);
        }
        return writtenBytes > 0;
    }

    private static IOException translateForConnected(Errors.NativeIoException e) {
        // We need to correctly translate connect errors to match NIO behaviour.
        if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
            PortUnreachableException error = new PortUnreachableException(e.getMessage());
            error.initCause(e);
            return error;
        }
        return e;
    }

    private static void checkUnresolved(AddressedEnvelope<?, ?> envelope) {
        if (envelope.recipient() instanceof InetSocketAddress
                && (((InetSocketAddress) envelope.recipient()).isUnresolved())) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (msg instanceof ControlMessageDatagramPacket) {
            ControlMessageDatagramPacket packet = (ControlMessageDatagramPacket) msg;
            checkUnresolved(packet);
            ControlMessages controlMessages = packet.controlMessages();
            // Fail fast if the control messages can't be sent.
            ControlMessageEncoder.encodedLength(controlMessages);
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new ControlMessageDatagramPacket(newDirectBuffer(packet, content), packet.recipient(),
                            controlMessages) : msg;
        }
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            checkUnresolved(packet);
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);

            if (e.content() instanceof ByteBuf &&
                    (e.recipient() == null || e.recipient() instanceof InetSocketAddress)) {

                ByteBuf content = (ByteBuf) e.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                        new DefaultAddressedEnvelope<ByteBuf, InetSocketAddress>(
                                newDirectBuffer(e, content), (InetSocketAddress) e.recipient()) : e;
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

    private DatagramPacket recvWithControlMessages(KQueueRecvByteAllocatorHandle allocHandle, ByteBuf byteBuf,
                                                   ControlMessageDecoder decoder, boolean connected)
            throws IOException {
        final long memoryAddress;
        final int pos;
        final int limit;
        if (byteBuf.hasMemoryAddress()) {
            memoryAddress = byteBuf.memoryAddress();
            pos = byteBuf.writerIndex();
            limit = byteBuf.capacity();
        } else {
            ByteBuffer nioData = byteBuf.internalNioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
            memoryAddress = Buffer.memoryAddress(nioData);
            pos = nioData.position();
            limit = nioData.limit();
        }
        ControlMessageBuffer controlMessageBuffer =
                ((NativeArrays) registration().attachment()).cleanControlMessageBuffer();
        int controlLength = decoder.controlLength();
        int controlOffset = controlMessageBuffer.reserve(controlLength);
        if (connected) {
            // We don't need to retrieve the address of the sender, which allows to not create any objects.
            final long res;
            try {
                res = socket.recvAddressControl(memoryAddress, pos, limit,
                        controlMessageBuffer.memoryAddress(controlOffset), controlLength);
            } catch (Errors.NativeIoException e) {
                throw translateForConnected(e);
            }
            int bytesRead = (int) (res >> 32);
            if (bytesRead < 0) {
                allocHandle.lastBytesRead(-1);
                return null;
            }
            ControlMessages controlMessages = decoder.decode(controlMessageBuffer.buffer(), controlOffset, (int) res);
            allocHandle.lastBytesRead(bytesRead);
            byteBuf.writerIndex(byteBuf.writerIndex() + bytesRead);
            return new ControlMessageDatagramPacket(byteBuf, (InetSocketAddress) localAddress(),
                    (InetSocketAddress) remoteAddress(), controlMessages);
        }
        DatagramSocketAddress remoteAddress = socket.recvFromAddress(memoryAddress, pos, limit,
                controlMessageBuffer.memoryAddress(controlOffset), controlLength);
        if (remoteAddress == null) {
            allocHandle.lastBytesRead(-1);
            return null;
        }
        ControlMessages controlMessages = decoder.decode(
                controlMessageBuffer.buffer(), controlOffset, remoteAddress.controlLength());
        InetSocketAddress localAddress = remoteAddress.localAddress();
        if (localAddress == null) {
            localAddress = (InetSocketAddress) localAddress();
        }
        allocHandle.lastBytesRead(remoteAddress.receivedAmount());
        byteBuf.writerIndex(byteBuf.writerIndex() + allocHandle.lastBytesRead());
        return new ControlMessageDatagramPacket(byteBuf, localAddress, remoteAddress, controlMessages);
    }

    final class KQueueDatagramChannelUnsafe extends AbstractKQueueUnsafe {

        @Override
        void readReady(KQueueRecvByteAllocatorHandle allocHandle) {
            assert eventLoop().inEventLoop();
            final DatagramChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);

            Throwable exception = null;
            try {
                ByteBuf byteBuf = null;
                try {
                    boolean connected = isConnected();
                    do {
                        byteBuf = allocHandle.allocate(allocator);
                        allocHandle.attemptedBytesRead(byteBuf.writableBytes());
                        // Fetch it for each read as it might be changed by the user while we are reading.
                        ControlMessageDecoder decoder = KQueueDatagramChannel.this.config.controlMessageDecoder();

                        final DatagramPacket packet;
                        if (decoder != null) {
                            packet = recvWithControlMessages(allocHandle, byteBuf, decoder, connected);
                            if (packet == null) {
                                byteBuf.release();
                                byteBuf = null;
                                break;
                            }
                        } else if (connected) {
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
                            packet = new DatagramPacket(byteBuf,
                                    (InetSocketAddress) localAddress(), (InetSocketAddress) remoteAddress());
                        } else {
                            final DatagramSocketAddress remoteAddress;
                            if (byteBuf.hasMemoryAddress()) {
                                // has a memory address so use optimized call
                                remoteAddress = socket.recvFromAddress(byteBuf.memoryAddress(), byteBuf.writerIndex(),
                                        byteBuf.capacity());
                            } else {
                                ByteBuffer nioData = byteBuf.internalNioBuffer(
                                        byteBuf.writerIndex(), byteBuf.writableBytes());
                                remoteAddress = socket.recvFrom(nioData, nioData.position(), nioData.limit());
                            }

                            if (remoteAddress == null) {
                                allocHandle.lastBytesRead(-1);
                                byteBuf.release();
                                byteBuf = null;
                                break;
                            }
                            InetSocketAddress localAddress = remoteAddress.localAddress();
                            if (localAddress == null) {
                                localAddress = (InetSocketAddress) localAddress();
                            }
                            allocHandle.lastBytesRead(remoteAddress.receivedAmount());
                            byteBuf.writerIndex(byteBuf.writerIndex() + allocHandle.lastBytesRead());

                            packet = new DatagramPacket(byteBuf, localAddress, remoteAddress);
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
                if (shouldStopReading(config)) {
                    clearReadFilter0();
                }
            }
        }
    }
}
