/*
 * Copyright 2014 The Netty Project
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
package io.netty5.channel.epoll;

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
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.internal.ChannelUtils;
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
import io.netty5.util.internal.StringUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static io.netty5.channel.ChannelOption.IP_TOS;
import static io.netty5.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty5.channel.ChannelOption.SO_LINGER;
import static io.netty5.channel.ChannelOption.SO_RCVBUF;
import static io.netty5.channel.ChannelOption.SO_REUSEADDR;
import static io.netty5.channel.ChannelOption.SO_SNDBUF;
import static io.netty5.channel.ChannelOption.TCP_NODELAY;
import static io.netty5.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_CLIENT;
import static io.netty5.channel.internal.ChannelUtils.MAX_BYTES_PER_GATHERING_WRITE_ATTEMPTED_LOW_THRESHOLD;
import static io.netty5.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
import static io.netty5.channel.unix.UnixChannelOption.DOMAIN_SOCKET_READ_MODE;
import static java.util.Objects.requireNonNull;

/**
 * {@link SocketChannel} implementation that uses linux EPOLL Edge-Triggered Mode for
 * maximal performance.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link SocketChannel} and {@link UnixChannel},
 * {@link EpollSocketChannel} allows the following options in the option map:
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
 * <td>{@link EpollChannelOption#TCP_CORK}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_NOTSENT_LOWAT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_KEEPCNT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_KEEPIDLE}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_KEEPINTVL}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_MD5SIG}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_QUICKACK}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#TCP_INFO}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_FASTOPEN_CONNECT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#IP_TRANSPARENT}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link EpollChannelOption#SO_BUSY_POLL}</td><td>X</td><td>X</td><td>-</td>
 * </tr><tr>
 * <td>{@link UnixChannelOption#SO_PEERCRED}</td><td></td><td></td><td>X</td>
 * </tr><tr>
 * <td>{@link UnixChannelOption#DOMAIN_SOCKET_READ_MODE}</td><td></td><td></td><td>X</td>
 * </tr>
 * </table>
 */
public final class EpollSocketChannel
        extends AbstractEpollChannel<EpollServerSocketChannel>
        implements SocketChannel {

    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS = supportedOptions();
    private static final Set<ChannelOption<?>> SUPPORTED_OPTIONS_DOMAIN_SOCKET = supportedOptionsDomainSocket();

    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(Buffer.class) + ", " +
                    StringUtil.simpleClassName(DefaultFileRegion.class) + ')';
    // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
    // meantime.
    private final Runnable flushTask = this::writeFlushed;

    private WritableByteChannel byteChannel;
    private volatile long maxBytesPerGatheringWrite = SSIZE_MAX;

    private volatile Collection<InetAddress> tcpMd5SigAddresses = Collections.emptyList();

    private volatile DomainSocketReadMode mode = DomainSocketReadMode.BYTES;

    private volatile boolean tcpFastopen;

    public EpollSocketChannel(EventLoop eventLoop) {
        this(eventLoop, (ProtocolFamily) null);
    }

    public EpollSocketChannel(EventLoop eventLoop, ProtocolFamily protocolFamily) {
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        super(null, eventLoop, false, Native.EPOLLRDHUP, new AdaptiveReadHandleFactory(),
                LinuxSocket.newSocket(protocolFamily), false);
    }

    public EpollSocketChannel(EventLoop eventLoop, int fd, ProtocolFamily family) {
        this(eventLoop, new LinuxSocket(fd, SocketProtocolFamily.of(family)));
    }

    private EpollSocketChannel(EventLoop eventLoop, LinuxSocket socket) {
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        super(null, eventLoop, false, Native.EPOLLRDHUP, new AdaptiveReadHandleFactory(),
                socket, isSoErrorZero(socket));
    }

    EpollSocketChannel(EpollServerSocketChannel parent, EventLoop eventLoop,
                       LinuxSocket fd, SocketAddress remoteAddress) {
        // Add EPOLLRDHUP so we are notified once the remote peer close the connection.
        super(parent, eventLoop, false, Native.EPOLLRDHUP, new AdaptiveReadHandleFactory(), fd, remoteAddress);

        if (fd.protocolFamily() != SocketProtocolFamily.UNIX && parent != null) {
            tcpMd5SigAddresses = parent.tcpMd5SigAddresses();
        }
    }

    /**
     * Write bytes form the given {@link Buffer} to the underlying {@link java.nio.channels.Channel}.
     * @param in the collection which contains objects to write.
     * @param buf the {@link Buffer} from which the bytes should be written
     * @return The value that should be decremented from the write-quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
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
        } else {
            ByteBuffer[] nioBuffers = new ByteBuffer[readableComponents];
            buf.forEachReadable(0, (index, component) -> {
                nioBuffers[index] = component.readableBuffer();
                return true;
            });
            return writeBytesMultiple(in, nioBuffers, nioBuffers.length, readableBytes,
                    getMaxBytesPerGatheringWrite());
        }
    }

    void setMaxBytesPerGatheringWrite(long maxBytesPerGatheringWrite) {
        this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
    }

    long getMaxBytesPerGatheringWrite() {
        return maxBytesPerGatheringWrite;
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
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
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
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write multiple bytes via {@link ByteBuffer} array.
     * @param in the collection which contains objects to write.
     * @param nioBuffers The buffers to write.
     * @param nioBufferCnt The number of buffers to write.
     * @param expectedWrittenBytes The number of bytes we expect to write.
     * @param maxBytesPerGatheringWrite The maximum number of bytes we should attempt to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
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
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write a {@link DefaultFileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link DefaultFileRegion} from which the bytes should be written
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     */
    private int writeDefaultFileRegion(ChannelOutboundBuffer in, DefaultFileRegion region) throws Exception {
        final long offset = region.transferred();
        final long regionCount = region.count();
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
        return WRITE_STATUS_SNDBUF_FULL;
    }

    /**
     * Write a {@link FileRegion}
     * @param in the collection which contains objects to write.
     * @param region the {@link FileRegion} from which the bytes should be written
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     */
    private int writeFileRegion(ChannelOutboundBuffer in, FileRegion region) throws Exception {
        if (region.transferred() >= region.count()) {
            in.remove();
            return 0;
        }

        if (byteChannel == null) {
            byteChannel = new EpollSocketWritableByteChannel();
        }
        final long flushedAmount = region.transferTo(byteChannel, region.transferred());
        if (flushedAmount > 0) {
            in.progress(flushedAmount);
            if (region.transferred() >= region.count()) {
                in.remove();
            }
            return 1;
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = getWriteSpinCount();
        do {
            final int msgCount = in.size();
            // Do gathering write if the outbound buffer entries start with more than one Buffer.
            if (msgCount > 1 && in.current() instanceof Buffer) {
                writeSpinCount -= doWriteMultiple(in);
            } else if (msgCount == 0) {
                // Wrote all messages.
                clearFlag(Native.EPOLLOUT);
                // Return here so we not set the EPOLLOUT flag.
                return;
            } else { // msgCount == 1
                writeSpinCount -= doWriteSingle(in);
            }

            // We do not break the loop here even if the outbound buffer was flushed completely,
            // because a user might have triggered another write and flush when we notify his or her
            // listeners.
        } while (writeSpinCount > 0);

        if (writeSpinCount == 0) {
            // It is possible that we have set EPOLLOUT, woken up by EPOLL because the socket is writable, and then use
            // our write quantum. In this case we no longer want to set the EPOLLOUT flag because the socket is still
            // writable (as far as we know). We will find out next time we attempt to write if the socket is writable
            // and set the EPOLLOUT if necessary.
            clearFlag(Native.EPOLLOUT);

            // We used our writeSpin quantum, and should try to write again later.
            executor().execute(flushTask);
        } else {
            // Underlying descriptor can not accept all data currently, so set the EPOLLOUT flag to be woken up
            // when it can accept more data.
            setFlag(Native.EPOLLOUT);
        }
    }

    /**
     * Attempt to write a single object.
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    private int doWriteSingle(ChannelOutboundBuffer in) throws Exception {
        // The outbound buffer contains only one message or it contains a file region.
        Object msg = in.current();
        if (msg instanceof FileDescriptor && socket.sendFd(((FileDescriptor) msg).intValue()) > 0) {
            // File descriptor was written, so remove it.
            in.remove();
            return 1;
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
     * @return The value that should be decremented from the write quantum which starts at
     * {@link #getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link Buffer} (or other empty content)
     *     is encountered</li>
     *     <li>1 - if a single call to write data was made to the OS</li>
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but
     *     no data was accepted</li>
     * </ul>
     * @throws Exception If an I/O error occurs.
     */
    private int doWriteMultiple(ChannelOutboundBuffer in) throws Exception {
        final long maxBytesPerGatheringWrite = getMaxBytesPerGatheringWrite();
        IovArray array = registration().cleanIovArray();
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

    @Override
    protected ReadState epollInReady(ReadBufferAllocator readBufferAllocator, BufferAllocator recvBufferAllocator,
                                     ReadSink readSink) throws Exception {
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX
                && getReadMode() == DomainSocketReadMode.FILE_DESCRIPTORS) {
            return epollInReadFd(readSink);
        }
        return epollInReadyBytes(readBufferAllocator, recvBufferAllocator, readSink);
    }

    private ReadState epollInReadyBytes(ReadBufferAllocator readBufferAllocator,
                                        BufferAllocator bufferAllocator, ReadSink readSink) throws Exception {
        Buffer buffer = null;
        boolean readMore;
        try {
            boolean continueReading;
            do {
                // we use a direct buffer here as the native implementations only be able
                // to handle direct buffers.
                buffer = readBufferAllocator.allocate(bufferAllocator, readSink.estimatedBufferCapacity());
                if (buffer == null) {
                    readSink.processRead(0, 0, null);
                    return ReadState.Partial;
                }
                assert buffer.isDirect();
                int attemptedBytesRead = buffer.writableBytes();
                int actualBytesRead = doReadBytes(buffer);

                readMore = attemptedBytesRead == actualBytesRead;
                if (actualBytesRead <= 0) {
                    // nothing was read, release the buffer.
                    Resource.dispose(buffer);
                    buffer = null;
                    readSink.processRead(attemptedBytesRead, actualBytesRead, null);

                    if (actualBytesRead < 0) {
                        return ReadState.Closed;
                    }
                    return ReadState.All;
                }
                continueReading = readSink.processRead(attemptedBytesRead, actualBytesRead, buffer);
                buffer = null;
            } while (continueReading && readMore && !isShutdown(ChannelShutdownDirection.Inbound));
            if (readMore) {
                return ReadState.Partial;
            }
            return ReadState.All;
        } catch (Throwable t) {
            if (buffer != null) {
                buffer.close();
            }
            throw t;
        }
    }

    private final class EpollSocketWritableByteChannel extends SocketWritableByteChannel {
        EpollSocketWritableByteChannel() {
            super(socket);
        }

        @Override
        protected BufferAllocator alloc() {
            return bufferAllocator();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (isOptionSupported(socket.protocolFamily(), option)) {
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
            if (option == EpollChannelOption.TCP_CORK) {
                return (T) Boolean.valueOf(isTcpCork());
            }
            if (option == EpollChannelOption.TCP_NOTSENT_LOWAT) {
                return (T) Long.valueOf(getTcpNotSentLowAt());
            }
            if (option == EpollChannelOption.TCP_KEEPIDLE) {
                return (T) Integer.valueOf(getTcpKeepIdle());
            }
            if (option == EpollChannelOption.TCP_KEEPINTVL) {
                return (T) Integer.valueOf(getTcpKeepIntvl());
            }
            if (option == EpollChannelOption.TCP_KEEPCNT) {
                return (T) Integer.valueOf(getTcpKeepCnt());
            }
            if (option == EpollChannelOption.TCP_USER_TIMEOUT) {
                return (T) Integer.valueOf(getTcpUserTimeout());
            }
            if (option == EpollChannelOption.TCP_QUICKACK) {
                return (T) Boolean.valueOf(isTcpQuickAck());
            }
            if (option == EpollChannelOption.IP_TRANSPARENT) {
                return (T) Boolean.valueOf(isIpTransparent());
            }
            if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
                return (T) Boolean.valueOf(isTcpFastOpenConnect());
            }
            if (option == EpollChannelOption.SO_BUSY_POLL) {
                return (T) Integer.valueOf(getSoBusyPoll());
            }
            if (option == DOMAIN_SOCKET_READ_MODE) {
                return (T) getReadMode();
            }
            if (option == EpollChannelOption.TCP_INFO) {
                return (T) getTcpInfo();
            }
            if (option == UnixChannelOption.SO_PEERCRED) {
                return (T) getPeerCredentials();
            }
        }
        return super.getExtendedOption(option);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (isOptionSupported(socket.protocolFamily(), option)) {
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
            } else if (option == EpollChannelOption.TCP_CORK) {
                setTcpCork((Boolean) value);
            } else if (option == EpollChannelOption.TCP_NOTSENT_LOWAT) {
                setTcpNotSentLowAt((Long) value);
            } else if (option == EpollChannelOption.TCP_KEEPIDLE) {
                setTcpKeepIdle((Integer) value);
            } else if (option == EpollChannelOption.TCP_KEEPCNT) {
                setTcpKeepCnt((Integer) value);
            } else if (option == EpollChannelOption.TCP_KEEPINTVL) {
                setTcpKeepIntvl((Integer) value);
            } else if (option == EpollChannelOption.TCP_USER_TIMEOUT) {
                setTcpUserTimeout((Integer) value);
            } else if (option == EpollChannelOption.IP_TRANSPARENT) {
                setIpTransparent((Boolean) value);
            } else if (option == EpollChannelOption.TCP_MD5SIG) {
                @SuppressWarnings("unchecked")
                final Map<InetAddress, byte[]> m = (Map<InetAddress, byte[]>) value;
                setTcpMd5Sig(m);
            } else if (option == EpollChannelOption.TCP_QUICKACK) {
                setTcpQuickAck((Boolean) value);
            } else if (option == ChannelOption.TCP_FASTOPEN_CONNECT) {
                setTcpFastOpenConnect((Boolean) value);
            } else if (option == EpollChannelOption.SO_BUSY_POLL) {
                setSoBusyPoll((Integer) value);
            } else if (option == DOMAIN_SOCKET_READ_MODE) {
                setReadMode((DomainSocketReadMode) value);
            } else if (option == EpollChannelOption.TCP_INFO) {
                throw new UnsupportedOperationException("read-only option: " + option);
            } else if (option == UnixChannelOption.SO_PEERCRED) {
                throw new UnsupportedOperationException("read-only option: " + option);
            }
        } else {
            super.setExtendedOption(option, value);
        }
    }

    private static boolean isOptionSupported(SocketProtocolFamily family, ChannelOption<?> option) {
        if (family == SocketProtocolFamily.UNIX) {
            return SUPPORTED_OPTIONS_DOMAIN_SOCKET.contains(option);
        }
        return SUPPORTED_OPTIONS.contains(option);
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        return isOptionSupported(socket.protocolFamily(), option) || super.isExtendedOptionSupported(option);
    }

    private static Set<ChannelOption<?>> supportedOptions() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_SNDBUF, TCP_NODELAY, SO_KEEPALIVE, SO_REUSEADDR, SO_LINGER,
                IP_TOS, EpollChannelOption.TCP_CORK, EpollChannelOption.TCP_KEEPIDLE, EpollChannelOption.TCP_KEEPCNT,
                EpollChannelOption.TCP_KEEPINTVL, EpollChannelOption.TCP_USER_TIMEOUT,
                EpollChannelOption.IP_TRANSPARENT, EpollChannelOption.TCP_MD5SIG, EpollChannelOption.TCP_QUICKACK,
                ChannelOption.TCP_FASTOPEN_CONNECT, EpollChannelOption.SO_BUSY_POLL,
                EpollChannelOption.TCP_NOTSENT_LOWAT, EpollChannelOption.TCP_INFO);
    }

    private static Set<ChannelOption<?>> supportedOptionsDomainSocket() {
        return newSupportedIdentityOptionsSet(SO_RCVBUF, SO_SNDBUF, DOMAIN_SOCKET_READ_MODE,
                UnixChannelOption.SO_PEERCRED);
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

    /**
     * Get the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    private boolean isTcpCork() {
        try {
            return socket.isTcpCork();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getSoBusyPoll() {
        try {
            return socket.getSoBusyPoll();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @return value is a uint32_t
     */
    private long getTcpNotSentLowAt() {
        try {
            return socket.getTcpNotSentLowAt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepIdle() {
        try {
            return socket.getTcpKeepIdle();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepIntvl() {
        try {
            return socket.getTcpKeepIntvl();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpKeepCnt() {
        try {
            return socket.getTcpKeepCnt();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Get the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private int getTcpUserTimeout() {
        try {
            return socket.getTcpUserTimeout();
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

    /**
     * Set the {@code TCP_CORK} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpCork(boolean tcpCork) {
        try {
            socket.setTcpCork(tcpCork);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code SO_BUSY_POLL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setSoBusyPoll(int loopMicros) {
        try {
            socket.setSoBusyPoll(loopMicros);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_NOTSENT_LOWAT} option on the socket. See {@code man 7 tcp} for more details.
     * @param tcpNotSentLowAt is a uint32_t
     */
    private void setTcpNotSentLowAt(long tcpNotSentLowAt) {
        try {
            socket.setTcpNotSentLowAt(tcpNotSentLowAt);
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
     * Set the {@code TCP_KEEPIDLE} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIdle(int seconds) {
        try {
            socket.setTcpKeepIdle(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPINTVL} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepIntvl(int seconds) {
        try {
            socket.setTcpKeepIntvl(seconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_KEEPCNT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpKeepCnt(int probes) {
        try {
            socket.setTcpKeepCnt(probes);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_USER_TIMEOUT} option on the socket. See {@code man 7 tcp} for more details.
     */
    private void setTcpUserTimeout(int milliseconds) {
        try {
            socket.setTcpUserTimeout(milliseconds);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} otherwise.
     */
    public boolean isIpTransparent() {
        try {
            return socket.isIpTransparent();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * If {@code true} is used <a href="https://man7.org/linux/man-pages/man7/ip.7.html">IP_TRANSPARENT</a> is enabled,
     * {@code false} for disable it. Default is disabled.
     */
    private void setIpTransparent(boolean transparent) {
        try {
            socket.setIpTransparent(transparent);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Set the {@code TCP_QUICKACK} option on the socket.
     * See <a href="https://linux.die.net//man/7/tcp">TCP_QUICKACK</a>
     * for more details.
     */
    private void setTcpQuickAck(boolean quickAck) {
        try {
            socket.setTcpQuickAck(quickAck);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    /**
     * Returns {@code true} if <a href="https://linux.die.net//man/7/tcp">TCP_QUICKACK</a> is enabled,
     * {@code false} otherwise.
     */
    private boolean isTcpQuickAck() {
        try {
            return socket.isTcpQuickAck();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void setReadMode(DomainSocketReadMode mode) {
        requireNonNull(mode, "mode");
        this.mode = mode;
    }

    private DomainSocketReadMode getReadMode() {
        return mode;
    }

    /**
     * Enables client TCP fast open. {@code TCP_FASTOPEN_CONNECT} normally
     * requires Linux kernel 4.11 or later, so instead we use the traditional fast open
     * client socket mechanics that work with kernel 3.6 and later. See this
     * <a href="https://lwn.net/Articles/508865/">LWN article</a> for more info.
     */
    private void setTcpFastOpenConnect(boolean fastOpenConnect) {
        this.tcpFastopen = fastOpenConnect;
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
            setMaxBytesPerGatheringWrite(newSendBufferSize);
        }
    }

    /**
     * Updates and returns the {@code TCP_INFO} for the current socket.
     * See <a href="https://linux.die.net//man/7/tcp">man 7 tcp</a>.
     */
    private EpollTcpInfo getTcpInfo() {
        try {
            EpollTcpInfo info = new EpollTcpInfo();
            socket.getTcpInfo(info);
            return info;
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected boolean doConnect0(SocketAddress remote) throws Exception {
        if (IS_SUPPORTING_TCP_FASTOPEN_CLIENT && socket.protocolFamily() != SocketProtocolFamily.UNIX &&
                isTcpFastOpenConnect()) {
            ChannelOutboundBuffer outbound = outboundBuffer();
            outbound.addFlush();
            Object curr = outbound.current();
            if (curr instanceof Buffer) {
                // If no cookie is present, the write fails with EINPROGRESS and this call basically
                // becomes a normal async connect. All writes will be sent normally afterwards.
                final long localFlushedAmount;
                Buffer initialData = (Buffer) curr;
                localFlushedAmount = doWriteOrSendBytes(initialData, remote, true);
                if (localFlushedAmount > 0) {
                    // We had a cookie and our fast-open proceeded. Remove written data
                    // then continue with normal TCP operation.
                    outbound.removeBytes(localFlushedAmount);
                    return true;
                }
            }
        }
        return super.doConnect0(remote);
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
                    executor().deregisterForIo(this).map(v -> GlobalEventExecutor.INSTANCE);
                }
            } catch (Throwable ignore) {
                // Ignore the error as the underlying channel may be closed in the meantime and so
                // getSoLinger() may produce an exception. In this case we just return null.
                // See https://github.com/netty/netty/issues/4449
            }
        }
        return null;
    }

    /**
     * Set the {@code TCP_MD5SIG} option on the socket. See {@code linux/tcp.h} for more details.
     * Keys can only be set on, not read to prevent a potential leak, as they are confidential.
     * Allowing them being read would mean anyone with access to the channel could get them.
     */
    private void setTcpMd5Sig(Map<InetAddress, byte[]> keys) {
        // Add synchronized as newTcpMp5Sigs might do multiple operations on the socket itself.
        synchronized (this) {
            try {
                tcpMd5SigAddresses = TcpMd5Util.newTcpMd5Sigs(this, tcpMd5SigAddresses, keys);
            } catch (IOException e) {
                throw new ChannelException(e);
            }
        }
    }

    private PeerCredentials getPeerCredentials() {
        try {
            return socket.getPeerCredentials();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private ReadState epollInReadFd(ReadSink readSink)
            throws Exception {
        boolean continueReading;
        do {
            int readFd = socket.recvFd();
            switch(readFd) {
                case 0:
                    readSink.processRead(0, 0, null);
                    return ReadState.All;
                case -1:
                    readSink.processRead(0, 0, null);
                    closeTransport(newPromise());
                    return ReadState.Closed;
                default:
                    continueReading = readSink.processRead(0, 0, new FileDescriptor(readFd));
                    break;
            }
        } while (continueReading
                && !isShutdown(ChannelShutdownDirection.Inbound));

        return ReadState.Partial;
    }
}
