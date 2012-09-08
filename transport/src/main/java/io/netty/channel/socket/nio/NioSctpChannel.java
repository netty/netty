/*
 * Copyright 2011 The Netty Project
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
package io.netty.channel.socket.nio;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ChannelBufType;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSctpChannelConfig;
import io.netty.channel.socket.SctpChannelConfig;
import io.netty.channel.socket.SctpData;
import io.netty.channel.socket.SctpNotificationHandler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NioSctpChannel extends AbstractNioMessageChannel implements io.netty.channel.socket.SctpChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.MESSAGE, false);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSctpChannel.class);

    private final SctpChannelConfig config;

    private final NotificationHandler notificationHandler;

    private static SctpChannel newSctpChannel() {
        try {
            return SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    public NioSctpChannel() {
        this(newSctpChannel());
    }

    public NioSctpChannel(SctpChannel sctpChannel) {
        this(null, null, sctpChannel);
    }

    public NioSctpChannel(Channel parent, Integer id, SctpChannel sctpChannel) {
        super(parent, id, sctpChannel, SelectionKey.OP_READ);
        try {
            sctpChannel.configureBlocking(false);
        } catch (IOException e) {
            try {
                sctpChannel.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized sctp channel.", e2);
                }

            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }

        config = new DefaultSctpChannelConfig(sctpChannel);
        notificationHandler = new SctpNotificationHandler(this);
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public Association association() {
        try {
            return javaChannel().association();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Set<SocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
            final Set<SocketAddress> addresses = new HashSet<SocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add(socketAddress);
            }
            return addresses;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    @Override
    public SctpChannelConfig config() {
        return config;
    }

    @Override
    public Set<SocketAddress> allRemoteAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getRemoteAddresses();
            final Set<SocketAddress> addresses = new HashSet<SocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add(socketAddress);
            }
            return addresses;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    @Override
    protected SctpChannel javaChannel() {
        return (SctpChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SctpChannel ch = javaChannel();
        return ch.isOpen() && association() != null;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            for (SocketAddress address : javaChannel().getAllLocalAddresses()) {
                return address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            for (SocketAddress address : javaChannel().getRemoteAddresses()) {
                return address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().bind(localAddress);
        }

        boolean success = false;
        try {
            boolean connected = javaChannel().connect(remoteAddress);
            if (connected) {
                selectionKey().interestOps(SelectionKey.OP_READ);
            } else {
                selectionKey().interestOps(SelectionKey.OP_CONNECT);
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
    protected void doFinishConnect() throws Exception {
        if (!javaChannel().finishConnect()) {
            throw new Error();
        }
        selectionKey().interestOps(SelectionKey.OP_READ);
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        SctpChannel ch = javaChannel();
        ByteBuffer data = ByteBuffer.allocate(config().getReceiveBufferSize());
        MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
        if (messageInfo == null) {
            return 0;
        }

        data.flip();
        buf.add(new SctpData(messageInfo, Unpooled.wrappedBuffer(data)));
        return 1;
    }

    @Override
    protected int doWriteMessages(MessageBuf<Object> buf, boolean lastSpin) throws Exception {
        SctpData packet = (SctpData) buf.peek();
        ByteBuf data = packet.getPayloadBuffer();
        int dataLen = data.readableBytes();
        ByteBuffer nioData;
        if (data.hasNioBuffer()) {
            nioData = data.nioBuffer();
        } else {
            nioData = ByteBuffer.allocate(dataLen);
            data.getBytes(data.readerIndex(), nioData);
            nioData.flip();
        }


        final MessageInfo messageInfo = MessageInfo.createOutgoing(association(), null, packet.getStreamIdentifier());
        messageInfo.payloadProtocolID(packet.getProtocolIdentifier());
        messageInfo.streamNumber(packet.getStreamIdentifier());

        final int writtenBytes = javaChannel().send(nioData, messageInfo);

        final SelectionKey key = selectionKey();
        final int interestOps = key.interestOps();
        if (writtenBytes <= 0 && dataLen > 0) {
            // Did not write a packet.
            // 1) If 'lastSpin' is false, the caller will call this method again real soon.
            //    - Do not update OP_WRITE.
            // 2) If 'lastSpin' is true, the caller will not retry.
            //    - Set OP_WRITE so that the event loop calls flushForcibly() later.
            if (lastSpin) {
                if ((interestOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(interestOps | SelectionKey.OP_WRITE);
                }
            }
            return 0;
        }

        // Wrote a packet.
        buf.remove();
        if (buf.isEmpty()) {
            // Wrote the outbound buffer completely - clear OP_WRITE.
            if ((interestOps & SelectionKey.OP_WRITE) != 0) {
                key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
            }
        }
        return 1;
    }
}
