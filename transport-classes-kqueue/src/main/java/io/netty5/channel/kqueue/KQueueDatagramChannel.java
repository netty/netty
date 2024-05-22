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

import io.netty5.buffer.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultBufferAddressedEnvelope;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.MaxMessagesWriteHandleFactory;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.DatagramSocketAddress;
import io.netty5.channel.unix.DomainDatagramSocketAddress;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PortUnreachableException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.util.Set;

import static io.netty5.channel.ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION;
import static io.netty5.channel.ChannelOption.IP_TOS;
import static io.netty5.channel.ChannelOption.SO_BROADCAST;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.unix.UnixChannelOption.SO_REUSEPORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * {@link DatagramChannel} implementation that uses KQueue.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link DatagramChannel} and {@link UnixChannel},
 * {@link KQueueDatagramChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>{@link ChannelOption}</th>
 * <th>{@code INET}</th>
 * <th>{@code INET6}</th>
 * <th>{@code UNIX}</th>
 * </tr><tr>
 * <td>{@link IntegerUnixChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link RawUnixChannelOption}</td><td>X</td><td>X</td><td>X</td>
 * </tr><tr>
 * <td>{@link UnixChannelOption#SO_REUSEPORT}</td><td>X</td><td>X</td><td>-</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class KQueueDatagramChannel
        extends AbstractKQueueChannel<UnixChannel> implements DatagramChannel {
    private static final Logger logger = LoggerFactory.getLogger(KQueueDatagramChannel.class);
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS_DOMAIN_SOCKET = supportedOptionsDomainSocket();

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(InetSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private static final String EXPECTED_TYPES_DOMAIN =
            " (expected: " +
                    StringUtil.simpleClassName(DatagramPacket.class) + ", " +
                    StringUtil.simpleClassName(AddressedEnvelope.class) + '<' +
                    StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(DomainSocketAddress.class) + ">, " +
                    StringUtil.simpleClassName(Buffer.class) + ')';

    private volatile boolean connected;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    private boolean activeOnOpen;

    public KQueueDatagramChannel(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, ProtocolFamily protocolFamily) {
        super(null, eventLoop, true, new FixedReadHandleFactory(2048),
                new MaxMessagesWriteHandleFactory(Integer.MAX_VALUE),
                BsdSocket.newDatagramSocket(protocolFamily), false);
    }

    public KQueueDatagramChannel(EventLoop eventLoop, int fd, ProtocolFamily protocolFamily) {
        this(eventLoop, new BsdSocket(fd, SocketProtocolFamily.of(protocolFamily)), true);
    }

    KQueueDatagramChannel(EventLoop eventLoop, BsdSocket socket, boolean active) {
        super(null, eventLoop, true, new FixedReadHandleFactory(2048),
                new MaxMessagesWriteHandleFactory(Integer.MAX_VALUE), socket, active);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (isSupported(socket.protocolFamily(), option)) {
            if (option == SO_BROADCAST) {
                return (T) Boolean.valueOf(isBroadcast());
            }
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == SO_REUSEADDR) {
                return (T) Boolean.valueOf(isReuseAddress());
            }
            if (option == IP_TOS) {
                return (T) Integer.valueOf(getTrafficClass());
            }
            if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
                return (T) Boolean.valueOf(activeOnOpen);
            }
            if (option == SO_REUSEPORT) {
                return (T) Boolean.valueOf(isReusePort());
            }
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected  <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (isSupported(socket.protocolFamily(), option)) {
            if (option == SO_BROADCAST) {
                setBroadcast((Boolean) value);
            } else if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == IP_TOS) {
                setTrafficClass((Integer) value);
            } else if (option == DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION) {
                setActiveOnOpen((Boolean) value);
            } else if (option == SO_REUSEPORT) {
                setReusePort((Boolean) value);
            }
        } else {
            super.setExtendedOption(option, value);
        }
    }

    private boolean isSupported(SocketProtocolFamily protocolFamily, ChannelOption<?> option) {
        if (protocolFamily == SocketProtocolFamily.UNIX) {
            return SUPPORTED_OPTIONS_DOMAIN_SOCKET.contains(option);
        }
        return SUPPORTED_OPTIONS.contains(option);
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        return isSupported(socket.protocolFamily(), option) || super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_BROADCAST, SO_RCVBUF, SO_SNDBUF, SO_REUSEADDR, IP_TOS,
                DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, SO_REUSEPORT);
    }

    private static Set<ChannelOption<?>> supportedOptionsDomainSocket() {
        return newSupportedIdentityOptionsSet(SO_SNDBUF, SO_RCVBUF, DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION);
    }

    private void setActiveOnOpen(boolean activeOnOpen) {
        if (isRegistered()) {
            throw new IllegalStateException("Can only changed before channel was registered");
        }
        this.activeOnOpen = activeOnOpen;
    }

    private boolean getActiveOnOpen() {
        return activeOnOpen;
    }

    /**
     * Returns {@code true} if the SO_REUSEPORT option is set.
     */
    private boolean isReusePort() {
        try {
            return socket.isReusePort();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the SO_REUSEPORT option on the underlying Channel. This will allow to bind multiple
     * {@link KQueueSocketChannel}s to the same port and so accept connections with multiple threads.
     *
     * Be aware this method needs be called before {@link KQueueDatagramChannel#bind(java.net.SocketAddress)} to have
     * any affect.
     */
    private void setReusePort(boolean reusePort) {
        try {
            socket.setReusePort(reusePort);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSendBufferSize() {
        try {
            return socket.getSendBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    public void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReceiveBufferSize(int receiveBufferSize) {
        try {
            socket.setReceiveBufferSize(receiveBufferSize);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getTrafficClass() {
        try {
            return socket.getTrafficClass();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTrafficClass(int trafficClass) {
        try {
            socket.setTrafficClass(trafficClass);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isReuseAddress() {
        try {
            return socket.isReuseAddress();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isBroadcast() {
        try {
            return socket.isBroadcast();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setBroadcast(boolean broadcast) {
        try {
            socket.setBroadcast(broadcast);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public boolean isActive() {
        return socket.isOpen() && (getActiveOnOpen() && isRegistered() || active);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        active = true;
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) {
        Object msg = writeSink.currentFlushedMessage();
        final Buffer data;
        SocketAddress remoteAddress;
        if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Buffer, SocketAddress> envelope = (AddressedEnvelope<Buffer, SocketAddress>) msg;
            data = envelope.content();
            remoteAddress = envelope.recipient();
        } else {
            data = (Buffer) msg;
            remoteAddress = null;
        }

        final int initialReadableBytes = data.readableBytes();
        if (initialReadableBytes == 0) {
            writeSink.complete(0, 0, 1, true);
            return;
        }

        try {
            final int written;
            if (data.countReadableComponents() > 1) {
                IovArray array = registration().ioHandler().cleanArray();
                array.addReadable(data);
                int count = array.count();
                assert count != 0;

                if (remoteAddress == null) {
                    // connected datagram
                     remoteAddress = remoteAddress();
                }
                if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                    written = socket.sendToAddressesDomainSocket(
                            array.memoryAddress(0), count,
                            ((DomainSocketAddress) remoteAddress).path().getBytes(UTF_8));
                } else {
                    InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                    written = socket.sendToAddresses(array.memoryAddress(0), count,
                            inetSocketAddress.getAddress(), inetSocketAddress.getPort());
                }
            } else {
                if (remoteAddress == null) {
                    try (var iteration = data.forEachComponent()) {
                        var component = iteration.firstReadable();
                        written = socket.writeAddress(component.readableNativeAddress(), 0,
                                component.readableBytes());
                    }
                } else {
                    if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                        byte[] path = ((DomainSocketAddress) remoteAddress).path().getBytes(UTF_8);
                        try (var iteration = data.forEachComponent()) {
                            var component = iteration.firstReadable();
                            written = socket.sendToAddressDomainSocket(
                                    component.readableNativeAddress(), 0, component.readableBytes(), path);
                        }
                    } else {
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
                        try (var iteration = data.forEachComponent()) {
                            var component = iteration.firstReadable();
                            written = socket.sendToAddress(component.readableNativeAddress(), 0,
                                    component.readableBytes(), inetSocketAddress.getAddress(),
                                    inetSocketAddress.getPort());
                        }
                    }
                }
            }
            writeSink.complete(initialReadableBytes, written, written > 0 ? 1 : 0, written > 0);
        } catch (IOException e) {
            // Continue on write error as a DatagramChannel can write to multiple remote peers
            //
            // See https://github.com/netty/netty/issues/2665
            writeSink.complete(initialReadableBytes, e, true);
        }
    }

    private static void checkUnresolved(AddressedEnvelope<?, ?> envelope) {
        if (envelope.recipient() instanceof InetSocketAddress
                && (((InetSocketAddress) envelope.recipient()).isUnresolved())) {
            throw new UnresolvedAddressException();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            return filterOutboundMessage0(msg, DomainSocketAddress.class, EXPECTED_TYPES_DOMAIN);
        } else {
            return filterOutboundMessage0(msg, InetSocketAddress.class, EXPECTED_TYPES);
        }
    }

    private Object filterOutboundMessage0(Object msg, Class<? extends SocketAddress> recipientClass,
                                            String expectedTypes) {
        if (msg instanceof DatagramPacket) {
            DatagramPacket packet = (DatagramPacket) msg;
            checkUnresolved(packet);

            if (recipientClass.isInstance(packet.recipient())) {
                Buffer content = packet.content();
                return UnixChannelUtil.isBufferCopyNeededForWrite(content)?
                        new DatagramPacket(newDirectBuffer(packet, content), packet.recipient()) : msg;
            }
        } else if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        } else if (msg instanceof AddressedEnvelope) {
            @SuppressWarnings("unchecked")
            AddressedEnvelope<Object, SocketAddress> e = (AddressedEnvelope<Object, SocketAddress>) msg;
            checkUnresolved(e);

            SocketAddress recipient = e.recipient();
            if (recipient == null || recipientClass.isInstance(recipient)) {
                if (e.content() instanceof Buffer) {
                    Buffer buf = (Buffer) e.content();
                    if (UnixChannelUtil.isBufferCopyNeededForWrite(buf)) {
                        try {
                            return new DefaultBufferAddressedEnvelope<>(newDirectBuffer(null, buf), recipient);
                        } finally {
                            SilentDispose.dispose(e, logger); // Don't fail here, because we allocated a buffer.
                        }
                    }
                    return e;
                }
            }
        }
        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + expectedTypes);
    }

    @Override
    protected void doDisconnect() throws Exception {
        socket.disconnect();
        connected = active = false;
        resetCachedAddresses();
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
        if (super.doConnect(remoteAddress, localAddress, initialData)) {
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

    @Override
    int readReady(ReadSink readSink) throws Exception {
        Buffer buffer = null;
        try {
            boolean connected = isConnected();
            buffer = readSink.allocateBuffer();
            if (buffer == null) {
                readSink.processRead(0, 0, null);
                return 0;
            }
            assert buffer.isDirect();
            int attemptedBytesRead = buffer.writableBytes();
            int actualBytesRead = 0;
            final DatagramPacket packet;
            if (connected) {
                try {
                    actualBytesRead = doReadBytes(buffer);
                } catch (Errors.NativeIoException e) {
                    // We need to correctly translate connect errors to match NIO behaviour.
                    if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
                        PortUnreachableException error = new PortUnreachableException(e.getMessage());
                        error.initCause(e);
                        throw error;
                    }
                    throw e;
                }

                if (actualBytesRead <= 0) {
                    // nothing was read, release the buffer.
                    buffer.close();
                    buffer = null;
                    readSink.processRead(attemptedBytesRead, actualBytesRead, null);
                    return actualBytesRead;
                }
                buffer.skipWritableBytes(actualBytesRead);
                packet = new DatagramPacket(buffer, localAddress(), remoteAddress());
            } else {
                SocketAddress localAddress = null;
                SocketAddress remoteAddress = null;
                if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                    DomainDatagramSocketAddress recvAddress = null;
                    try (var iteration = buffer.forEachComponent()) {
                        var c = iteration.firstWritable();
                        if (c != null) {
                            recvAddress = socket.recvFromAddressDomainSocket(
                                    c.writableNativeAddress(), 0, c.writableBytes());
                        }
                    }
                    if (recvAddress != null) {
                        remoteAddress = recvAddress;
                        actualBytesRead = recvAddress.receivedAmount();
                        localAddress = recvAddress.localAddress();
                    }
                } else {
                    try (var iterator = buffer.forEachComponent()) {
                        var component = iterator.firstWritable();
                        long addr = component.writableNativeAddress();
                        DatagramSocketAddress datagramSocketAddress;
                        if (addr != 0) {
                            // has a memory address so use optimized call
                            datagramSocketAddress = socket.recvFromAddress(addr, 0, component.writableBytes());
                        } else {
                            ByteBuffer nioData = component.writableBuffer();
                            datagramSocketAddress = socket.recvFrom(
                                    nioData, nioData.position(), nioData.limit());
                        }
                        if (datagramSocketAddress != null) {
                            remoteAddress = datagramSocketAddress;
                            localAddress = datagramSocketAddress.localAddress();
                            actualBytesRead = datagramSocketAddress.receivedAmount();
                        }
                    }
                }

                if (remoteAddress == null) {
                    readSink.processRead(attemptedBytesRead, 0, null);
                    buffer.close();
                    buffer = null;
                    return 0;
                }
                if (localAddress == null) {
                    localAddress = localAddress();
                }
                buffer.skipWritableBytes(actualBytesRead);

                packet = new DatagramPacket(buffer, localAddress, remoteAddress);
            }
            readSink.processRead(attemptedBytesRead, actualBytesRead, packet);
            buffer = null;
            return actualBytesRead;
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            throw t;
        }
    }

    private <V> Future<V> newMulticastNotSupportedFuture() {
        return newFailedFuture(new UnsupportedOperationException("Multicast not supported"));
    }

    @Override
    public Future<Void> joinGroup(InetAddress multicastAddress) {
        requireNonNull(multicastAddress, "multicast");
        return newMulticastNotSupportedFuture();
    }

    @Override
    public Future<Void> joinGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        return newMulticastNotSupportedFuture();
    }

    @Override
    public Future<Void> leaveGroup(InetAddress multicastAddress) {
        requireNonNull(multicastAddress, "multicast");
        return newMulticastNotSupportedFuture();
    }

    @Override
    public Future<Void> leaveGroup(
            InetAddress multicastAddress, NetworkInterface networkInterface, InetAddress source) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(networkInterface, "networkInterface");

        return newMulticastNotSupportedFuture();
    }

    @Override
    public Future<Void> block(
            InetAddress multicastAddress, NetworkInterface networkInterface,
            InetAddress sourceToBlock) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");
        requireNonNull(networkInterface, "networkInterface");

        return newMulticastNotSupportedFuture();
    }

    @Override
    public Future<Void> block(InetAddress multicastAddress, InetAddress sourceToBlock) {
        requireNonNull(multicastAddress, "multicastAddress");
        requireNonNull(sourceToBlock, "sourceToBlock");

        return newMulticastNotSupportedFuture();
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        switch (direction) {
            case Inbound:
                inputShutdown = true;
                break;
            case Outbound:
                outputShutdown = true;
                break;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        if (!isActive()) {
            return true;
        }
        switch (direction) {
            case Inbound:
                return inputShutdown;
            case Outbound:
                return outputShutdown;
            default:
                throw new AssertionError();
        }
    }
}
