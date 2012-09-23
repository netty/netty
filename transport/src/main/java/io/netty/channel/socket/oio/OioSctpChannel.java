/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.oio;

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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.socket.DefaultSctpChannelConfig;
import io.netty.channel.socket.SctpChannelConfig;
import io.netty.channel.socket.SctpData;
import io.netty.channel.socket.SctpNotificationHandler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class OioSctpChannel extends AbstractOioMessageChannel
        implements io.netty.channel.socket.SctpChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(ChannelBufType.MESSAGE, false);

    private final SctpChannel ch;
    private final SctpChannelConfig config;
    private final NotificationHandler<?> notificationHandler;

    private static SctpChannel openChannel() {
        try {
            return SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    public OioSctpChannel() {
        this(openChannel());
    }

    public OioSctpChannel(SctpChannel ch) {
        this(null, null, ch);
    }

    public OioSctpChannel(Channel parent, Integer id, SctpChannel ch) {
        super(parent, id);
        this.ch = ch;
        boolean success = false;
        try {
            ch.configureBlocking(true);
            config = new DefaultSctpChannelConfig(ch);
            notificationHandler = new SctpNotificationHandler(this);
            success = true;
        } catch (Exception e) {
            throw new ChannelException("failed to initialize a sctp channel", e);
        } finally {
            if (!success) {
                try {
                    ch.close();
                } catch (IOException e) {
                    logger.warn("Failed to close a sctp channel.", e);
                }
            }
        }
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public SctpChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    protected int doReadMessages(MessageBuf<Object> buf) throws Exception {
        if (readSuspended) {
            return 0;
        }

        ByteBuffer data = ByteBuffer.allocate(config().getReceiveBufferSize());
        MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
        if (messageInfo == null) {
            return 0;
        }

        data.flip();
        buf.add(new SctpData(messageInfo, Unpooled.wrappedBuffer(data)));

        if (readSuspended) {
            return 0;
        } else {
            return 1;
        }

    }

    @Override
    protected void doWriteMessages(MessageBuf<Object> buf) throws Exception {
        SctpData packet = (SctpData) buf.poll();
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


        final MessageInfo mi = MessageInfo.createOutgoing(association(), null, packet.getStreamIdentifier());
        mi.payloadProtocolID(packet.getProtocolIdentifier());
        mi.streamNumber(packet.getStreamIdentifier());

        ch.send(nioData, mi);
    }

    @Override
    public Association association() {
        try {
            return ch.association();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isActive() {
        return isOpen() && association() != null;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            for (SocketAddress address : ch.getAllLocalAddresses()) {
                return address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<SocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getAllLocalAddresses();
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
    protected SocketAddress remoteAddress0() {
        try {
            for (SocketAddress address : ch.getRemoteAddresses()) {
                return address;
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<SocketAddress> allRemoteAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getRemoteAddresses();
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
    protected void doBind(SocketAddress localAddress) throws Exception {
        ch.bind(localAddress);
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress,
                             SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            ch.bind(localAddress);
        }

        boolean success = false;
        try {
            ch.connect(remoteAddress);
            success = true;
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
    protected void doClose() throws Exception {
        ch.close();
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        ChannelFuture future = newFuture();
        doBindAddress(localAddress, future);
        return future;
    }

    void doBindAddress(final InetAddress localAddress, final ChannelFuture future) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.bindAddress(localAddress);
                future.setSuccess();
            } catch (Throwable t) {
                future.setFailure(t);
                pipeline().fireExceptionCaught(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    doBindAddress(localAddress, future);
                }
            });
        }
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        ChannelFuture future = newFuture();
        doUnbindAddress(localAddress, future);
        return future;
    }

    void doUnbindAddress(final InetAddress localAddress, final ChannelFuture future) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.unbindAddress(localAddress);
                future.setSuccess();
            } catch (Throwable t) {
                future.setFailure(t);
                pipeline().fireExceptionCaught(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    doUnbindAddress(localAddress, future);
                }
            });
        }
    }
}
