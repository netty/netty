/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.udt.nio;

import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.SocketChannelUDT;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioByteChannel;
import io.netty.channel.nio.NioIoOps;
import io.netty.channel.udt.DefaultUdtChannelConfig;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.UdtChannelConfig;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Byte Channel Connector for UDT Streams.
 *
 * @deprecated The UDT transport is no longer maintained and will be removed.
 */
@Deprecated
public class NioUdtByteConnectorChannel extends AbstractNioByteChannel implements UdtChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NioUdtByteConnectorChannel.class);

    private final UdtChannelConfig config;

    public NioUdtByteConnectorChannel() {
        this(TypeUDT.STREAM);
    }

    public NioUdtByteConnectorChannel(final Channel parent, final SocketChannelUDT channelUDT) {
        super(parent, channelUDT);
        try {
            channelUDT.configureBlocking(false);
            switch (channelUDT.socketUDT().status()) {
            case INIT:
            case OPENED:
                config = new DefaultUdtChannelConfig(this, channelUDT, true);
                break;
            default:
                config = new DefaultUdtChannelConfig(this, channelUDT, false);
                break;
            }
        } catch (final Exception e) {
            try {
                channelUDT.close();
            } catch (final Exception e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to close channel.", e2);
                }
            }
            throw new ChannelException("Failed to configure channel.", e);
        }
    }

    public NioUdtByteConnectorChannel(final SocketChannelUDT channelUDT) {
        this(null, channelUDT);
    }

    public NioUdtByteConnectorChannel(final TypeUDT type) {
        this(NioUdtProvider.newConnectorChannelUDT(type));
    }

    @Override
    public UdtChannelConfig config() {
        return config;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) throws Exception {
        privilegedBind(javaChannel(), localAddress);
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected boolean doConnect(final SocketAddress remoteAddress,
                                final SocketAddress localAddress) throws Exception {
        doBind(localAddress != null? localAddress : new InetSocketAddress(0));
        boolean success = false;
        try {
            final boolean connected = SocketUtils.connect(javaChannel(), remoteAddress);
            if (!connected) {
                addAndSubmit(NioIoOps.CONNECT);
            }
            success = true;
            return connected;
        } finally {
            if (!success) {
                doClose();
            }
        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doFinishConnect() throws Exception {
        if (javaChannel().finishConnect()) {
            removeAndSubmit(NioIoOps.CONNECT);
        } else {
            throw new Error(
                    "Provider error: failed to finish connect. Provider library should be upgraded.");
        }
    }

    @Override
    protected int doReadBytes(final ByteBuf byteBuf) throws Exception {
        final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        allocHandle.attemptedBytesRead(byteBuf.writableBytes());
        return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());
    }

    @Override
    protected int doWriteBytes(final ByteBuf byteBuf) throws Exception {
        final int expectedWrittenBytes = byteBuf.readableBytes();
        return byteBuf.readBytes(javaChannel(), expectedWrittenBytes);
    }

    @Override
    protected ChannelFuture shutdownInput() {
        return newFailedFuture(new UnsupportedOperationException("shutdownInput"));
    }

    @Override
    protected long doWriteFileRegion(FileRegion region) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isActive() {
        final SocketChannelUDT channelUDT = javaChannel();
        return channelUDT.isOpen() && channelUDT.isConnectFinished();
    }

    @Override
    protected SocketChannelUDT javaChannel() {
        return (SocketChannelUDT) super.javaChannel();
    }

    @Override
    protected SocketAddress localAddress0() {
        return javaChannel().socket().getLocalSocketAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return javaChannel().socket().getRemoteSocketAddress();
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    private static void privilegedBind(final SocketChannelUDT socketChannel, final SocketAddress localAddress)
            throws IOException {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws IOException {
                    socketChannel.bind(localAddress);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

}
