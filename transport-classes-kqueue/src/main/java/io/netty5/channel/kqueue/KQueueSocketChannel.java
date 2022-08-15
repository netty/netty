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
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundBuffer;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultFileRegion;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.DomainSocketReadMode;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.channel.unix.IntegerUnixChannelOption;
import io.netty5.channel.unix.IovArray;
import io.netty5.channel.unix.PeerCredentials;
import io.netty5.channel.unix.RawUnixChannelOption;
import io.netty5.channel.unix.SocketWritableByteChannel;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.channel.unix.UnixChannelOption;
import io.netty5.channel.unix.UnixChannelUtil;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.StringUtil;
import io.netty5.util.internal.UnstableApi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.netty5.channel.ChannelOption.IP_TOS;
import static io.netty5.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty5.channel.ChannelOption.SO_LINGER;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.ChannelOption.TCP_NODELAY;
import static io.netty5.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;
import static io.netty5.channel.kqueue.KQueueChannelOption.SO_SNDLOWAT;
import static io.netty5.channel.kqueue.KQueueChannelOption.TCP_NOPUSH;
import static io.netty5.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;
import static java.util.Objects.requireNonNull;

/**
 * {@link SocketChannel} implementation that uses KQueue.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannel} and {@link UnixChannel},
 * {@link KQueueSocketChannel} allows the following options in the option map:
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
 * <td>{@link KQueueChannelOption#SO_SNDLOWAT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link KQueueChannelOption#TCP_NOPUSH}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_FASTOPEN_CONNECT}</td><td>X</td><td>X</td><td>-</td>
 * </tr>
 * </table>
 */
@UnstableApi
public final class KQueueSocketChannel
        extends AbstractKQueueChannel<KQueueServerSocketChannel>
        implements SocketChannel {
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS_DOMAIN_SOCKET = supportedOptionsDomainSocket();

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(DefaultFileRegion.class) + ')';
    private WritableByteChannel byteChannel;

    // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
    // meantime.
    private final Runnable flushTask = this::writeFlushed;

    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    private volatile boolean tcpFastopen;

    public KQueueSocketChannel(EventLoop eventLoop) {
        this(eventLoop, (ProtocolFamily) null);
    }

    public KQueueSocketChannel(EventLoop eventLoop, ProtocolFamily protocolFamily) {
        super(null, eventLoop, false, new AdaptiveReadHandleFactory(), BsdSocket.newSocket(protocolFamily), false);
        enableTcpNoDelayIfSupported();
        calculateMaxBytesPerGatheringWrite();
    }

    public KQueueSocketChannel(EventLoop eventLoop, int fd, ProtocolFamily protocolFamily) {
        this(eventLoop, new BsdSocket(fd, SocketProtocolFamily.of(protocolFamily)));
    }

    private KQueueSocketChannel(EventLoop eventLoop, BsdSocket fd) {
        super(null, eventLoop, false, new AdaptiveReadHandleFactory(), fd, isSoErrorZero(fd));
        enableTcpNoDelayIfSupported();
        calculateMaxBytesPerGatheringWrite();
    }

    KQueueSocketChannel(KQueueServerSocketChannel parent, EventLoop eventLoop,
                        BsdSocket fd, SocketAddress remoteAddress) {
        super(parent, eventLoop, false, new AdaptiveReadHandleFactory(), fd, remoteAddress);
        enableTcpNoDelayIfSupported();
        calculateMaxBytesPerGatheringWrite();
    }

    private void enableTcpNoDelayIfSupported() {
        if (socket.protocolFamily() != SocketProtocolFamily.UNIX && PlatformDependent.canEnableTcpNoDelayByDefault()) {
            setTcpNoDelay(true);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (isSupported(socket.protocolFamily(), option)) {
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == TCP_NODELAY) {
                return (T) Boolean.valueOf(isTcpNoDelay());
            }
            if (option == SO_KEEPALIVE) {
                return (T) Boolean.valueOf(isKeepAlive());
            }
            if (option == SO_REUSEADDR) {
                return (T) Boolean.valueOf(isReuseAddress());
            }
            if (option == SO_LINGER) {
                return (T) Integer.valueOf(getSoLinger());
            }
            if (option == IP_TOS) {
                return (T) Integer.valueOf(getTrafficClass());
            }
            if (option == SO_SNDLOWAT) {
                return (T) Integer.valueOf(getSndLowAt());
            }
            if (option == TCP_NOPUSH) {
                return (T) Boolean.valueOf(isTcpNoPush());
            }
            if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
                return (T) Boolean.valueOf(isTcpFastOpenConnect());
            }
            if (option == DOMAIN_SOCKET_READ_MODE) {
                return (T) getReadMode();
            }
            if (option == UnixChannelOption.SO_PEERCRED) {
                return (T) getPeerCredentials();
            }
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (isSupported(socket.protocolFamily(), option)) {
            if (option == SO_RCVBUF) {
                setReceiveBufferSize((Integer) value);
            } else if (option == SO_SNDBUF) {
                setSendBufferSize((Integer) value);
            } else if (option == TCP_NODELAY) {
                setTcpNoDelay((Boolean) value);
            } else if (option == SO_KEEPALIVE) {
                setKeepAlive((Boolean) value);
            } else if (option == SO_REUSEADDR) {
                setReuseAddress((Boolean) value);
            } else if (option == SO_LINGER) {
                setSoLinger((Integer) value);
            } else if (option == IP_TOS) {
                setTrafficClass((Integer) value);
            } else if (option == SO_SNDLOWAT) {
                setSndLowAt((Integer) value);
            } else if (option == TCP_NOPUSH) {
                setTcpNoPush((Boolean) value);
            } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
                setTcpFastOpenConnect((Boolean) value);
            } else if (option == DOMAIN_SOCKET_READ_MODE) {
                setReadMode((DomainSocketReadMode) value);
            } else if (option == UnixChannelOption.SO_PEERCRED) {
                throw new UnsupportedOperationException("read-only option: " + option);
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
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_SNDBUF, TCP_NODELAY,
                SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER, IP_TOS, SO_SNDLOWAT, TCP_NOPUSH,
                ChannelOption.TCP_FASTOPEN_CONNECT);
    }

    private static Set<ChannelOption<?>> supportedOptionsDomainSocket() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_SNDBUF, DOMAIN_SOCKET_READ_MODE,
                UnixChannelOption.SO_PEERCRED);
    }

    private void setReadMode(DomainSocketReadMode mode) {
        requireNonNull(mode, "mode");
        this.mode = mode;
    }

    private DomainSocketReadMode getReadMode() {
        return mode;
    }

    private int getReceiveBufferSize() {
        try {
            return socket.getReceiveBufferSize();
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

    private int getSoLinger() {
        try {
            return socket.getSoLinger();
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

    private boolean isKeepAlive() {
        try {
            return socket.isKeepAlive();
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

    private boolean isTcpNoDelay() {
        try {
            return socket.isTcpNoDelay();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private int getSndLowAt() {
        try {
            return socket.getSndLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSndLowAt(int sndLowAt)  {
        try {
            socket.setSndLowAt(sndLowAt);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private boolean isTcpNoPush() {
        try {
            return socket.isTcpNoPush();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoPush(boolean tcpNoPush)  {
        try {
            socket.setTcpNoPush(tcpNoPush);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setKeepAlive(boolean keepAlive) {
        try {
            socket.setKeepAlive(keepAlive);
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

    private void setReuseAddress(boolean reuseAddress) {
        try {
            socket.setReuseAddress(reuseAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSendBufferSize(int sendBufferSize) {
        try {
            socket.setSendBufferSize(sendBufferSize);
            calculateMaxBytesPerGatheringWrite();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setSoLinger(int soLinger) {
        try {
            socket.setSoLinger(soLinger);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setTcpNoDelay(boolean tcpNoDelay) {
        try {
            socket.setTcpNoDelay(tcpNoDelay);
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

    /**
     * Enables client TCP fast open, if available.
     */
    private void setTcpFastOpenConnect(boolean fastOpenConnect) {
        tcpFastopen = fastOpenConnect;
    }

    /**
     * Returns {@code true} if TCP fast open is enabled, {@code false} otherwise.
     */
    private boolean isTcpFastOpenConnect() {
        return tcpFastopen;
    }

    private void calculateMaxBytesPerGatheringWrite() {
        // Multiply by 2 to give some extra space in case the OS can process write data faster than we can provide.
        int newSendBufferSize = getSendBufferSize() << 1;
        if (newSendBufferSize > 0) {
            setMaxBytesPerGatheringWrite(getSendBufferSize() << 1);
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX && msg instanceof FileDescriptor) {
            return msg;
        }
        if (msg instanceof Buffer) {
            Buffer buf = (Buffer) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf)? newDirectBuffer(buf) : buf;
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (isTcpFastOpenConnect()) {
            ChannelOutboundBuffer outbound = outboundBuffer();
            outbound.addFlush();
            Object curr;
            if ((curr = outbound.current()) instanceof Buffer) {
                Buffer initialData = (Buffer) curr;
                // Don't bother with TCP FastOpen if we don't have any initial data to send anyway.
                if (initialData.readableBytes() > 0) {
                    IovArray iov = new IovArray();
                    try {
                        initialData.forEachReadable(0, iov);
                        int bytesSent = socket.connectx(
                                (InetSocketAddress) localAddress, (InetSocketAddress) remoteAddress, iov, true);
                        writeFilter(true);
                        outbound.removeBytes(Math.abs(bytesSent));
                        // The `connectx` method returns a negative number if connection is in-progress.
                        // So we should return `true` to indicate that connection was established, if it's positive.
                        return bytesSent > 0;
                    } finally {
                        iov.release();
                    }
                }
            }
        }
        return super.doConnect0(remoteAddress, localAddress);
    }

    @Override
    protected Future<Executor> prepareToClose() {
        if (socket.protocolFamily() != SocketProtocolFamily.UNIX) {
            try {
                // Check isOpen() first as otherwise it will throw a RuntimeException
                // when call getSoLinger() as the fd is not valid anymore.
                if (isOpen() && getSoLinger() > 0) {
                    // We need to cancel this key of the channel so we may not end up in a eventloop spin
                    // because we try to read or write until the actual close happens which may be later due
                    // SO_LINGER handling.
                    // See https://github.com/netty/netty/issues/4449
                    return executor().deregisterForIo(this).map(v -> GlobalEventExecutor.INSTANCE);
                }
            } catch (Throwable ignore) {
                // Ignore the error as the underlying channel may be closed in the meantime and so
                // getSoLinger() may produce an exception. In this case we just return null.
                // See https://github.com/netty/netty/issues/4449
            }
        }
        return null;
    }

    @Override
    int readReady(ReadSink readSink) throws Exception {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX &&
                getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
            return readReadyFd(readSink);
        }
        return readReadyBytes(readSink);
    }

    private int readReadyFd(ReadSink readSink) throws Exception {
        int totalBytesRead = 0;
        boolean continueReading;
        readLoop: do {
            // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
            // KQueueRecvBufferAllocatorHandle knows if it should try to read again or not when autoRead is
            // enabled.
            int recvFd = socket.recvFd();
            switch(recvFd) {
                case 0:
                    readSink.processRead(0, 0, null);
                    break readLoop;
                case -1:
                    readSink.processRead(0, 0, null);
                    closeTransportNow();
                    return totalBytesRead;
                default:
                    totalBytesRead ++;
                    continueReading = readSink.processRead(0, 0, new FileDescriptor(recvFd));
                    break;
            }
        } while (continueReading && !isShutdown(ChannelShutdownDirection.Inbound));

        return totalBytesRead;
    }

    private PeerCredentials getPeerCredentials() {
        try {
            return socket.getPeerCredentials();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Write bytes form the given {@link Buffer} to the underlying {@link java.nio.channels.Channel}.
     * @param in the collection which contains objects to write.
     * @param buf the {@link Buffer} from which the bytes should be written
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     */
    private int writeBytes(ChannelOutboundBuffer in, Buffer buf) throws Exception {
        int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            in.remove();
            return 0;
        }

        int readableComponents = buf.countReadableComponents();
        if (readableComponents == 1) {
            return doWriteBytes(in, buf);
        }
        ByteBuffer[] nioBuffers = new ByteBuffer[readableComponents];
        buf.forEachReadable(0, (index, component) -> {
            nioBuffers[index] = component.readableBuffer();
            return true;
        });
        return writeBytesMultiple(in, nioBuffers, nioBuffers.length, readableBytes,
                getMaxBytesPerGatheringWrite());
    }

    private void adjustMaxBytesPerGatheringWrite(long attempted, long written, long oldMaxBytesPerGatheringWrite) {
        // By default we track the SO_SNDBUF when ever it is explicitly set. However some OSes may dynamically change
        // SO_SNDBUF (and other characteristics that determine how much data can be written at once) so we should try
        // make a best effort to adjust as OS behavior changes.
        if (attempted == written) {
            if (attempted << 1 > oldMaxBytesPerGatheringWrite) {
                setMaxBytesPerGatheringWrite(attempted << 1);
            }
        } else if (attempted > MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD && written < attempted >>> 1) {
            setMaxBytesPerGatheringWrite(attempted >>> 1);
        }
    }

    /**
     * Write multiple bytes via {@link IovArray}.
     * @param in the collection which contains objects to write.
     * @param array The array which contains the content to write.
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws IOException If an I/O exception occurs during write.
     */
    private int writeBytesMultiple(ChannelOutboundBuffer in, IovArray array) throws IOException {
        final long expectedWrittenBytes = array.size();
        assert expectedWrittenBytes != 0;
        final int cnt = array.count();
        assert cnt != 0;

        final long localWrittenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
        if (localWrittenBytes > 0) {
            adjustMaxBytesPerGatheringWrite(expectedWrittenBytes, localWrittenBytes, array.maxBytes());
            in.removeBytes(localWrittenBytes);
            return 1;
        }
        return -1;
    }

    /**
     * Write multiple bytes via {@link ByteBuffer} array.
     * @param in the collection which contains objects to write.
     * @param nioBuffers The buffers to write.
     * @param nioBufferCnt The number of buffers to write.
     * @param expectedWrittenBytes The number of bytes we expect to write.
     * @param maxBytesPerGatheringWrite The maximum number of bytes we should attempt to write.
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws IOException If an I/O exception occurs during write.
     */
    private int writeBytesMultiple(
            ChannelOutboundBuffer in, ByteBuffer[] nioBuffers, int nioBufferCnt, long expectedWrittenBytes,
            long maxBytesPerGatheringWrite) throws IOException {
        assert expectedWrittenBytes != 0;
        if (expectedWrittenBytes > maxBytesPerGatheringWrite) {
            expectedWrittenBytes = maxBytesPerGatheringWrite;
        }

        final long localWrittenBytes = socket.writev(nioBuffers, 0, nioBufferCnt, expectedWrittenBytes);
        if (localWrittenBytes > 0) {
            adjustMaxBytesPerGatheringWrite(expectedWrittenBytes, localWrittenBytes, maxBytesPerGatheringWrite);
            in.removeBytes(localWrittenBytes);
            return 1;
        }
        return -1;
    }

    /**
     * Write a {@link DefaultFileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link DefaultFileRegion} from which the bytes should be written
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     */
    private int writeDefaultFileRegion(ChannelOutboundBuffer in, DefaultFileRegion region) throws Exception {
        final long regionCount = region.count();
        final long offset = region.transferred();

        if (offset >= regionCount) {
            in.remove();
            return 0;
        }

        final long flushedAmount = socket.sendFile(region, region.position(), offset, regionCount - offset);
        if (flushedAmount > 0) {
            in.progress(flushedAmount);
            if (region.transferred() >= regionCount) {
                in.remove();
            }
            return 1;
        }
        if (flushedAmount == 0) {
            validateFileRegion(region, offset);
        }
        return -1;
    }

    /**
     * Write a {@link FileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link FileRegion} from which the bytes should be written
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     */
    private int writeFileRegion(ChannelOutboundBuffer in, FileRegion region) throws Exception {
        if (region.transferred() >= region.count()) {
            in.remove();
            return 0;
        }

        if (byteChannel == null) {
            byteChannel = new KQueueSocketWritableByteChannel();
        }
        final long flushedAmount = region.transferTo(byteChannel, region.transferred());
        if (flushedAmount > 0) {
            in.progress(flushedAmount);
            if (region.transferred() >= region.count()) {
                in.remove();
            }
            return 1;
        }
        return -1;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        while (!in.isEmpty()) {
            final int msgCount = in.size();
            int result;

            // Do gathering write if the outbound buffer entries start with more than one Buffer.
            if (msgCount > 1 && in.current() instanceof Buffer) {
                result = doWriteMultiple(in);
            } else if (msgCount == 0) {
                // Wrote all messages.
                writeFilter(false);
                // Return here so we don't set the WRITE flag.
                return;
            } else { // msgCount == 1
                result = doWriteSingle(in);
            }
            if (result == -1) {
                // Underlying descriptor can not accept all data currently, so set the WRITE flag to be woken up
                // when it can accept more data.
                writeFilter(true);
                return;
            }
        }
        writeFilter(false);
    }

    /**
     * Attempt to write a single object.
     * @param in the collection which contains objects to write.
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    private int doWriteSingle(ChannelOutboundBuffer in) throws Exception {
        // The outbound buffer contains only one message or it contains a file region.
        Object msg = in.current();
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            if (msg instanceof FileDescriptor && socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
                // File descriptor was written, so remove it.
                in.remove();
                return 1;
            }
        }

        if (msg instanceof Buffer) {
            return writeBytes(in, (Buffer) msg);
        } else if (msg instanceof DefaultFileRegion) {
            return writeDefaultFileRegion(in, (DefaultFileRegion) msg);
        } else if (msg instanceof FileRegion) {
            return writeFileRegion(in, (FileRegion) msg);
        } else {
            // Should never reach here.
            throw new Error();
        }
    }

    /**
     * Attempt to write multiple {@link Buffer} objects.
     * @param in the collection which contains objects to write.
     * @return write result.
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>-1 - if an attempt to write data was made to the OS, but no
     *     data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    private int doWriteMultiple(ChannelOutboundBuffer in) throws Exception {
        final long maxBytesPerGatheringWrite = getMaxBytesPerGatheringWrite();
        IovArray array = registration().cleanArray();
        array.maxBytes(maxBytesPerGatheringWrite);
        in.forEachFlushedMessage(array);

        if (array.count() >= 1) {
            return writeBytesMultiple(in, array);
        }
        // cnt == 0, which means the outbound buffer contained empty buffers only.
        in.removeBytes(0);
        return 0;
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) throws Exception {
        switch (direction) {
            case Outbound:
                socket.shutdown(false, true);
                break;
            case Inbound:
                try {
                    socket.shutdown(true, false);
                } catch (NotYetConnectedException ignore) {
                    // We attempted to shutdown and failed, which means the input has already effectively been
                    // shutdown.
                }
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
            case Outbound:
                return socket.isOutputShutdown();
            case Inbound:
                return socket.isInputShutdown();
            default:
                throw new AssertionError();
        }
    }

    private int readReadyBytes(ReadSink readSink) throws Exception {
        Buffer buffer = null;
        int totalBytesRead = 0;
        try {
            boolean continueReading;
            do {
                buffer = readSink.allocateBuffer();
                if (buffer == null) {
                    readSink.processRead(0, 0, null);
                    break;
                }
                // we use a direct buffer here as the native implementations only be able
                // to handle direct buffers.
                assert buffer.isDirect();
                int attemptedBytesRead = buffer.writableBytes();
                int actualBytesRead = doReadBytes(buffer);

                if (actualBytesRead <= 0) {
                    // nothing was read, release the buffer.
                    Resource.dispose(buffer);
                    buffer = null;
                    readSink.processRead(attemptedBytesRead, actualBytesRead, null);
                    if (actualBytesRead < 0) {
                        return -1;
                    }
                    break;
                }
                totalBytesRead += actualBytesRead;
                continueReading = readSink.processRead(attemptedBytesRead, actualBytesRead, buffer);
                buffer = null;
            } while (continueReading && !isShutdown(ChannelShutdownDirection.Inbound));
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            if (isConnectPending()) {
                finishConnect();
            }
            throw t;
        }
        return totalBytesRead;
    }

    private final class KQueueSocketWritableByteChannel extends SocketWritableByteChannel {
        KQueueSocketWritableByteChannel() {
            super(socket);
        }

        @Override
        protected BufferAllocator alloc() {
            return bufferAllocator();
        }
    }
}
