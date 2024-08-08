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
package io.netty5.channel.uring;

import io.netty5.buffer.Buffer;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannelReadHandleFactory;
import io.netty5.channel.ServerChannelWriteHandleFactory;
import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketChannelWriteHandleFactory;
import io.netty5.channel.socket.SocketProtocolFamily;
import io.netty5.channel.unix.Errors;
import io.netty5.channel.unix.UnixChannel;
import io.netty5.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.netty5.channel.unix.Buffer.allocateDirectWithNativeOrder;
import static io.netty5.channel.unix.Buffer.free;
import static io.netty5.channel.unix.Buffer.nativeAddressOf;
import static io.netty5.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty5.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;
import static io.netty5.channel.unix.Limits.SSIZE_MAX;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

public final class IoUringServerSocketChannel extends AbstractIoUringChannel<UnixChannel>
        implements ServerSocketChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger(IoUringDatagramChannel.class);
    private static final short IS_ACCEPT = 1;
    private final ByteBuffer sockaddrMemory;
    private final long sockaddrPtr;
    private final long addrlenPtr;
    private final EventLoopGroup childEventLoopGroup;

    // The maximum number of bytes for an InetAddress / Inet6Address
    private final byte[] inet4AddressArray = new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
    private final byte[] inet6AddressArray = new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];

    private volatile int backlog = NetUtil.SOMAXCONN;

    public IoUringServerSocketChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        super(null, eventLoop, false, new ServerChannelReadHandleFactory(), new ServerChannelWriteHandleFactory(),
                LinuxSocket.newSocketStream(), null, false);
        this.childEventLoopGroup = childEventLoopGroup;
        sockaddrMemory = allocateDirectWithNativeOrder(Long.BYTES + Native.SIZEOF_SOCKADDR_STORAGE);
        // Needs to be initialized to the size of acceptedAddressMemory.
        // See https://man7.org/linux/man-pages/man2/accept.2.html
        sockaddrMemory.putLong(0, Native.SIZEOF_SOCKADDR_STORAGE); // todo do this before enqueueing every accept?
        addrlenPtr = nativeAddressOf(sockaddrMemory);
        sockaddrPtr = addrlenPtr + Long.BYTES;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        super.doBind(localAddress);
        socket.listen(getBacklog());
        active = true;
    }

    @Override
    protected void doRead(boolean wasReadPendingAlready) throws Exception {
        if (!wasReadPendingAlready) {
            IoUringIoRegistration registration = registration();
            IoUringIoOps ops = IoUringIoOps.newAccept(fd().intValue(), 0, 0,
                    sockaddrPtr, addrlenPtr, IS_ACCEPT);
            registration.submit(ops);
        }
    }

    @Override
    void readComplete(int res, long udata) {
        currentCompletionResult = res;
        currentCompletionData = UserData.decodeData(udata);
        readNow();
    }

    @Override
    protected boolean doReadNow(ReadSink readSink) throws IOException {
        int res = currentCompletionResult;
        short data = currentCompletionData;
        if (data != IS_ACCEPT) {
            readSink.processRead(0, 0, null);
            return false;
        }
        currentCompletionResult = 0;
        currentCompletionData = 0;
        if (res >= 0) {
            Channel channel = newChildChannel(res);
            readSink.processRead(1, 1, channel);
        } else if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Check if we failed because there was nothing to accept atm.
            readSink.processRead(0, 0, null);
        } else {
            // Something bad happened. Convert to an exception.
            throw Errors.newIOException("io_uring accept", res);
        }
        return false;
    }

    @Override
    protected boolean processRead(ReadSink readSink, Object read) {
        throw new UnsupportedOperationException();
    }

    private Channel newChildChannel(int fd) {
        final SocketAddress peer;
        if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
            peer = null;
        } else {
            peer = buildAddress();
        }
        return new IoUringSocketChannel(
                this, childEventLoopGroup().next(), false,
                new AdaptiveReadHandleFactory(), new SocketChannelWriteHandleFactory(Integer.MAX_VALUE, SSIZE_MAX),
                LinuxSocket.wrapBlocking(fd, socket.protocolFamily()), peer, true);
    }

    private SocketAddress buildAddress() {
        if (socket.isIpv6()) {
            return SockaddrIn.readIPv6(sockaddrPtr, inet6AddressArray, inet4AddressArray);
        }
        return SockaddrIn.readIPv4(sockaddrPtr, inet4AddressArray);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Buffer initialData) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void submitAllWriteMessages(WriteSink writeSink) {
        throw new UnsupportedOperationException();
    }

    @Override
    void writeComplete(int result, long udata) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doShutdown(ChannelShutdownDirection direction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown(ChannelShutdownDirection direction) {
        return !isActive();
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }

    @Override
    protected void doClose() {
        try {
            super.doClose();
        } finally {
            free(sockaddrMemory);
            if (socket.protocolFamily() == SocketProtocolFamily.UNIX) {
                DomainSocketAddress local = (DomainSocketAddress) localAddress();
                if (local != null) {
                    try {
                        if (!Files.deleteIfExists(Path.of(local.path()))) {
                            logger().debug("Failed to delete domain socket file: {}", local.path());
                        }
                    } catch (IOException e) {
                        logger().debug("Failed to delete domain socket file: {}", local.path(), e);
                    }
                }
            }
        }
    }

    @Override
    public EventLoopGroup childEventLoopGroup() {
        return childEventLoopGroup;
    }

    @Override
    protected <T> T getExtendedOption(ChannelOption<T> option) {
        if (option == ChannelOption.SO_BACKLOG) {
            return (T) Integer.valueOf(getBacklog());
        }
        return super.getExtendedOption(option);
    }

    @Override
    protected <T> void setExtendedOption(ChannelOption<T> option, T value) {
        if (option == ChannelOption.SO_BACKLOG) {
            setBacklog((Integer) value);
        } else {
            super.setExtendedOption(option, value);
        }
    }

    @Override
    protected boolean isExtendedOptionSupported(ChannelOption<?> option) {
        if (option == ChannelOption.SO_BACKLOG) {
            return true;
        }
        return super.isExtendedOptionSupported(option);
    }

    private int getBacklog() {
        return backlog;
    }

    private void setBacklog(int backlog) {
        checkPositiveOrZero(backlog, "backlog");
        this.backlog = backlog;
    }
}
