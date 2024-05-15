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
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.DefaultFileRegion;
import io.netty5.channel.EventLoop;
import io.netty5.channel.FileRegion;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.SocketChannelWriteHandleFactory;
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
import io.netty5.util.concurrent.Promise;
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
import static io.netty5.channel.kqueue.KQueueChannelOption.SO_SNDLOWAT;
import static io.netty5.channel.kqueue.KQueueChannelOption.TCP_NOPUSH;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
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

    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    private volatile boolean tcpFastopen;

    public KQueueSocketChannel(EventLoop eventLoop) {
        this(eventLoop, (ProtocolFamily) null);
    }

    public KQueueSocketChannel(EventLoop eventLoop, ProtocolFamily protocolFamily) {
        super(null, eventLoop, false, new AdaptiveReadHandleFactory(),
                new SocketChannelWriteHandleFactory(Integer.MAX_VALUE, SSIZE_MAX),
                BsdSocket.newSocket(protocolFamily), false);
        enableTcpNoDelayIfSupported();
    }

    public KQueueSocketChannel(EventLoop eventLoop, int fd, ProtocolFamily protocolFamily) {
        this(eventLoop, new BsdSocket(fd, SocketProtocolFamily.of(protocolFamily)));
    }

    private KQueueSocketChannel(EventLoop eventLoop, BsdSocket fd) {
        super(null, eventLoop, false, new AdaptiveReadHandleFactory(),
                new SocketChannelWriteHandleFactory(Integer.MAX_VALUE), fd, isSoErrorZero(fd));
        enableTcpNoDelayIfSupported();
    }

    KQueueSocketChannel(KQueueServerSocketChannel parent, EventLoop eventLoop,
                        BsdSocket fd, SocketAddress remoteAddress) {
        super(parent, eventLoop, false, new AdaptiveReadHandleFactory(),
                new SocketChannelWriteHandleFactory(Integer.MAX_VALUE), fd, remoteAddress);
        enableTcpNoDelayIfSupported();
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
    protected boolean doConnect0(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData)
            throws Exception {
        if (isTcpFastOpenConnect()) {
            // Don't bother with TCP FastOpen if we don't have any initial data to send anyway.
            if (initialData != null && initialData.readableBytes() > 0) {
                IovArray iov = new IovArray();
                try {
                    iov.addReadable(initialData);
                    int bytesSent = socket.connectx(
                            (InetSocketAddress) localAddress, (InetSocketAddress) remoteAddress, iov, true);
                    writeFilter(true);

                    initialData.skipReadableBytes(Math.abs(bytesSent));
                    // The `connectx` method returns a negative number if connection is in-progress.
                    // So we should return `true` to indicate that connection was established, if it's positive.
                    return bytesSent > 0;
                } finally {
                    iov.release();
                }
            }
        }
        return super.doConnect0(remoteAddress, localAddress, initialData);
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
                    readFilter(false);
                    writeFilter(false);
                    Promise<Void> promise = newPromise();
                    deregisterTransport(promise);
                    return promise.asFuture().map(v -> GlobalEventExecutor.INSTANCE);
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
        // lastBytesRead represents the fd. We use lastBytesRead because it must be set so that the
        // KQueueRecvBufferAllocatorHandle knows if it should try to read again or not when autoRead is
        // enabled.
        int recvFd = socket.recvFd();
        switch(recvFd) {
            case 0:
                readSink.processRead(0, 0, null);
                return 0;
            case -1:
                readSink.processRead(0, 0, null);
                closeTransportNow();
                return -1;
            default:
                readSink.processRead(0, 0, new FileDescriptor(recvFd));
                return 1;
        }
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
     * @param   writeSink the {@link WriteSink} used.
     */
    private void writeBytes(WriteSink writeSink) throws Exception {
        Buffer buf = (Buffer) writeSink.currentFlushedMessage();
        int readableBytes = buf.readableBytes();
        if (readableBytes == 0) {
            writeSink.complete(0, 0, 1, true);
            return;
        }

        int readableComponents = buf.countReadableComponents();
        final long attempted;
        final int written;
        if (readableComponents == 1) {
            attempted = readableBytes;
            written = doWriteBytes(buf);
        }  else {
            attempted = Math.min(writeSink.estimatedMaxBytesPerGatheringWrite(), buf.readableBytes());
            ByteBuffer[] nioBuffers = new ByteBuffer[readableComponents];
            try (var iteration = buf.forEachComponent()) {
                int index = 0;
                for (var c = iteration.first(); c != null; c = c.next()) {
                    nioBuffers[index++] = c.readableBuffer();
                }
                written = (int) writeBytesMultiple(nioBuffers, nioBuffers.length, readableBytes, attempted);
            }
        }
        if (written > 0) {
            buf.skipReadableBytes(written);
        }
        writeSink.complete(attempted, written, readableBytes == written ? 1 : 0, written > 0);
    }

    /**
     * Write multiple bytes via {@link IovArray}.
     * @param array The array which contains the content to write.
     * @return number of written bytes.
     * @throws IOException If an I/O exception occurs during write.
     */
    private long writeBytesMultiple(IovArray array)
            throws IOException {
        final long expectedWrittenBytes = array.size();
        assert expectedWrittenBytes != 0;
        final int cnt = array.count();
        assert cnt != 0;

        return socket.writevAddresses(array.memoryAddress(0), cnt);
    }

    /**
     * Write multiple bytes via {@link ByteBuffer} array.
     * @param nioBuffers The buffers to write.
     * @param nioBufferCnt The number of buffers to write.
     * @param expectedWrittenBytes The number of bytes we expect to write.
     * @param maxBytesPerGatheringWrite The maximum number of bytes we should attempt to write.
     * @return number of written bytes.
     * @throws IOException If an I/O exception occurs during write.
     */
    private long writeBytesMultiple(ByteBuffer[] nioBuffers, int nioBufferCnt, long expectedWrittenBytes,
            long maxBytesPerGatheringWrite) throws IOException {
        assert expectedWrittenBytes != 0;
        if (expectedWrittenBytes > maxBytesPerGatheringWrite) {
            expectedWrittenBytes = maxBytesPerGatheringWrite;
        }

        return socket.writev(nioBuffers, 0, nioBufferCnt, expectedWrittenBytes);
    }

    /**
     * Write a {@link DefaultFileRegion}
     * @param   writeSink the {@link WriteSink} used.
     */
    private void writeDefaultFileRegion(WriteSink writeSink) throws Exception {
        final DefaultFileRegion region = (DefaultFileRegion) writeSink.currentFlushedMessage();
        final long regionCount = region.count();
        final long transferred = region.transferred();

        if (transferred >= regionCount) {
            writeSink.complete(0, 0, 1, true);
        } else {
            long flushedAmount = socket.sendFile(region, region.position(), transferred, regionCount - transferred);
            if (flushedAmount == 0) {
                validateFileRegion(region, transferred);
            }
            writeSink.complete(regionCount, flushedAmount, region.transferred() >= regionCount ? 1: 0,
                    flushedAmount > 0);
        }
    }

    /**
     * Write a {@link FileRegion}
     * @param   writeSink the {@link WriteSink} used.
     */
    private void writeFileRegion(WriteSink writeSink) throws Exception {
        final FileRegion region = (FileRegion) writeSink.currentFlushedMessage();
        final long regionCount = region.count();
        final long transferred = region.transferred();
        if (transferred >= regionCount) {
            writeSink.complete(0, 0, 1, true);
        } else {
            if (byteChannel == null) {
                byteChannel = new KQueueSocketWritableByteChannel();
            }
            long flushedAmount = region.transferTo(byteChannel, region.transferred());
            writeSink.complete(regionCount, flushedAmount, region.transferred() >= regionCount ? 1: 0,
                    flushedAmount > 0);
        }
    }

    @Override
    protected void doWriteNow(WriteSink writeSink) throws Exception {
        final int msgCount = writeSink.numFlushedMessages();
        // Do gathering write if the outbound buffer entries start with more than one Buffer.
        if (msgCount > 1 && writeSink.currentFlushedMessage() instanceof Buffer) {
            doWriteMultiple(writeSink);
        } else {
            doWriteSingle(writeSink);
        }
    }

    /**
     * Attempt to write a single object.
     * @param   writeSink the {@link WriteSink} used.
     * @throws Exception If an I/O error occurs.
     */
    private void doWriteSingle(WriteSink writeSink) throws Exception {
        // The outbound buffer contains only one message or it contains a file region.
        Object msg = writeSink.currentFlushedMessage();
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            if (msg instanceof FileDescriptor) {
                if (socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
                    // File descriptor was written, so remove it.
                    writeSink.complete(0, 0, 1, true);
                } else {
                    writeSink.complete(0, 0, 0, false);
                }
                return;
            }
        }

        if (msg instanceof Buffer) {
            writeBytes(writeSink);
        } else if (msg instanceof DefaultFileRegion) {
            writeDefaultFileRegion(writeSink);
        } else if (msg instanceof FileRegion) {
            writeFileRegion(writeSink);
        } else {
            // Should never reach here.
            throw new Error();
        }
    }

    /**
     * Attempt to write multiple {@link Buffer} objects.
     * @param   writeSink the {@link WriteSink} used.
     * @throws Exception If an I/O error occurs.
     */
    private void doWriteMultiple(WriteSink writeSink)
            throws Exception {
        IovArray array = registration().ioHandler().cleanArray();
        array.maxBytes(writeSink.estimatedMaxBytesPerGatheringWrite());
        writeSink.forEachFlushedMessage(array);

        if (array.count() >= 1) {
            long result = writeBytesMultiple(array);
            // Update readerOffset of buffers and return how many are completely written.
            int messages = writeSink.updateBufferReaderOffsets(result);
            writeSink.complete(array.size(), result, messages, result > 0);
        } else {
            // cnt == 0, which means the outbound buffer contained empty buffers only.
            writeSink.complete(0, 0, writeSink.numFlushedMessages(), true);
        }
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
        try {
            buffer = readSink.allocateBuffer();
            if (buffer == null) {
                readSink.processRead(0, 0, null);
                return 0;
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
                return 0;
            }
            buffer.skipWritableBytes(actualBytesRead);
            readSink.processRead(attemptedBytesRead, actualBytesRead, buffer);
            buffer = null;
            return actualBytesRead;
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            if (isConnectPending()) {
                finishConnect();
            }
            throw t;
        }
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
