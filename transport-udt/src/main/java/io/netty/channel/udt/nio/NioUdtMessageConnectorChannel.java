/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.udt.DefaultUdtChannelConfig;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.UdtChannelConfig;
import io.netty.channel.udt.UdtMessage;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static java.nio.channels.SelectionKey.*;

/**
 * Message Connector for UDT Datagrams.
 * <p>
 * Note: send/receive must use {@link UdtMessage} in the pipeline
 */
public class NioUdtMessageConnectorChannel extends AbstractNioMessageChannel implements UdtChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NioUdtMessageConnectorChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final UdtChannelConfig config;

    public NioUdtMessageConnectorChannel() {
        this(TypeUDT.DATAGRAM);
    }

    public NioUdtMessageConnectorChannel(final Channel parent, final SocketChannelUDT channelUDT) {
        super(parent, channelUDT, OP_READ);
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

    public NioUdtMessageConnectorChannel(final SocketChannelUDT channelUDT) {
        this(null, channelUDT);
    }

    public NioUdtMessageConnectorChannel(final TypeUDT type) {
        this(NioUdtProvider.newConnectorChannelUDT(type));
    }

    @Override
    public UdtChannelConfig config() {
        return config;
    }

    @Override
    protected void doBind(final SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
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
            final boolean connected = javaChannel().connect(remoteAddress);
            if (!connected) {
                selectionKey().interestOps(
                        selectionKey().interestOps() | OP_CONNECT);
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
            selectionKey().interestOps(
                    selectionKey().interestOps() & ~OP_CONNECT);
        } else {
            throw new Error(
                    "Provider error: failed to finish connect. Provider library should be upgraded.");
        }
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {

        final int maximumMessageSize = config.getReceiveBufferSize();

        final ByteBuf byteBuf = config.getAllocator().directBuffer(
                maximumMessageSize);

        final int receivedMessageSize = byteBuf.writeBytes(javaChannel(),
                maximumMessageSize);

        if (receivedMessageSize <= 0) {
            byteBuf.release();
            return 0;
        }

        if (receivedMessageSize >= maximumMessageSize) {
            javaChannel().close();
            throw new ChannelException(
                    "Invalid config : increase receive buffer size to avoid message truncation");
        }

        // delivers a message
        buf.add(new UdtMessage(byteBuf));

        return 1;
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        // expects a message
        final UdtMessage message = (UdtMessage) msg;

        final ByteBuf byteBuf = message.content();

        final int messageSize = byteBuf.readableBytes();
        if (messageSize == 0) {
            return true;
        }

        final long writtenBytes;
        if (byteBuf.nioBufferCount() == 1) {
            writtenBytes = javaChannel().write(byteBuf.nioBuffer());
        } else {
            writtenBytes = javaChannel().write(byteBuf.nioBuffers());
        }

        // wrote message completely
        if (writtenBytes > 0 && writtenBytes != messageSize) {
            throw new Error(
                    "Provider error: failed to write message. Provider library should be upgraded.");
        }

        return writtenBytes > 0;
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
    public ChannelMetadata metadata() {
        return METADATA;
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
}
