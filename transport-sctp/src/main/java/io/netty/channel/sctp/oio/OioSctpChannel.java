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
package io.netty.channel.sctp.oio;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.oio.AbstractOioMessageChannel;
import io.netty.channel.sctp.DefaultSctpChannelConfig;
import io.netty.channel.sctp.SctpChannelConfig;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpNotificationHandler;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link io.netty.channel.sctp.SctpChannel} implementation which use blocking mode and allows to read / write
 * {@link SctpMessage}s to the underlying {@link SctpChannel}.
 *
 * Be aware that not all operations systems support SCTP. Please refer to the documentation of your operation system,
 * to understand what you need to do to use it. Also this feature is only supported on Java 7+.
 */
public class OioSctpChannel extends AbstractOioMessageChannel
        implements io.netty.channel.sctp.SctpChannel {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OioSctpChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final String EXPECTED_TYPE = " (expected: " + StringUtil.simpleClassName(SctpMessage.class) + ')';

    private final SctpChannel ch;
    private final SctpChannelConfig config;

    private final Selector readSelector;
    private final Selector writeSelector;
    private final Selector connectSelector;

    private final NotificationHandler<?> notificationHandler;

    private RecvByteBufAllocator.Handle allocHandle;

    private static SctpChannel openChannel() {
        try {
            return SctpChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    /**
     * Create a new instance with an new {@link SctpChannel}.
     */
    public OioSctpChannel() {
        this(openChannel());
    }

    /**
     * Create a new instance from the given {@link SctpChannel}.
     *
     * @param ch    the {@link SctpChannel} which is used by this instance
     */
    public OioSctpChannel(SctpChannel ch) {
        this(null, ch);
    }

    /**
     * Create a new instance from the given {@link SctpChannel}.
     *
     * @param parent    the parent {@link Channel} which was used to create this instance. This can be null if the
     *                  {@link} has no parent as it was created by your self.
     * @param ch        the {@link SctpChannel} which is used by this instance
     */
    public OioSctpChannel(Channel parent, SctpChannel ch) {
        super(parent);
        this.ch = ch;
        boolean success = false;
        try {
            ch.configureBlocking(false);
            readSelector = Selector.open();
            writeSelector = Selector.open();
            connectSelector = Selector.open();

            ch.register(readSelector, SelectionKey.OP_READ);
            ch.register(writeSelector, SelectionKey.OP_WRITE);
            ch.register(connectSelector, SelectionKey.OP_CONNECT);

            config = new OioSctpChannelConfig(this, ch);
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
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public SctpServerChannel parent() {
        return (SctpServerChannel) super.parent();
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
    protected int doReadMessages(List<Object> msgs) throws Exception {
        if (!readSelector.isOpen()) {
            return 0;
        }

        int readMessages = 0;

        final int selectedKeys = readSelector.select(SO_TIMEOUT);
        final boolean keysSelected = selectedKeys > 0;

        if (!keysSelected) {
            return readMessages;
        }

        // We must clear the selectedKeys because the Selector will never do it. If we do not clear it, the selectionKey
        // will always be returned even if there is no data can be read which causes performance issue. And in some
        // implementation of Selector, the select method may return 0 if the selectionKey which is ready for process has
        // already been in the selectedKeys and cause the keysSelected above to be false even if we actually have
        // something to read.
        readSelector.selectedKeys().clear();

        RecvByteBufAllocator.Handle allocHandle = this.allocHandle;
        if (allocHandle == null) {
            this.allocHandle = allocHandle = config().getRecvByteBufAllocator().newHandle();
        }

        ByteBuf buffer = allocHandle.allocate(config().getAllocator());
        boolean free = true;

        try {
            ByteBuffer data = buffer.nioBuffer(buffer.writerIndex(), buffer.writableBytes());
            MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
            if (messageInfo == null) {
                return readMessages;
            }

            data.flip();
            msgs.add(new SctpMessage(messageInfo, buffer.writerIndex(buffer.writerIndex() + data.remaining())));
            free = false;
            readMessages ++;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
        }  finally {
            int bytesRead = buffer.readableBytes();
            allocHandle.record(bytesRead);
            if (free) {
                buffer.release();
            }
        }
        return readMessages;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        if (!writeSelector.isOpen()) {
            return;
        }
        final int size = in.size();
        final int selectedKeys = writeSelector.select(SO_TIMEOUT);
        if (selectedKeys > 0) {
            final Set<SelectionKey> writableKeys = writeSelector.selectedKeys();
            if (writableKeys.isEmpty()) {
                return;
            }
            Iterator<SelectionKey> writableKeysIt = writableKeys.iterator();
            int written = 0;
            for (;;) {
                if (written == size) {
                    // all written
                    return;
                }
                writableKeysIt.next();
                writableKeysIt.remove();

                SctpMessage packet = (SctpMessage) in.current();
                if (packet == null) {
                    return;
                }

                ByteBuf data = packet.content();
                int dataLen = data.readableBytes();
                ByteBuffer nioData;

                if (data.nioBufferCount() != -1) {
                    nioData = data.nioBuffer();
                } else {
                    nioData = ByteBuffer.allocate(dataLen);
                    data.getBytes(data.readerIndex(), nioData);
                    nioData.flip();
                }

                final MessageInfo mi = MessageInfo.createOutgoing(association(), null, packet.streamIdentifier());
                mi.payloadProtocolID(packet.protocolIdentifier());
                mi.streamNumber(packet.streamIdentifier());
                mi.unordered(packet.isUnordered());

                ch.send(nioData, mi);
                written ++;
                in.remove();

                if (!writableKeysIt.hasNext()) {
                    return;
                }
            }
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) throws Exception {
        if (msg instanceof SctpMessage) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPE);
    }

    @Override
    public Association association() {
        try {
            return ch.association();
        } catch (IOException ignored) {
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
            Iterator<SocketAddress> i = ch.getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<InetSocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getAllLocalAddresses();
            final Set<InetSocketAddress> addresses = new LinkedHashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            Iterator<SocketAddress> i = ch.getRemoteAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    public Set<InetSocketAddress> allRemoteAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = ch.getRemoteAddresses();
            final Set<InetSocketAddress> addresses = new LinkedHashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
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
            boolean  finishConnect = false;
            while (!finishConnect) {
                if (connectSelector.select(SO_TIMEOUT) >= 0) {
                    final Set<SelectionKey> selectionKeys = connectSelector.selectedKeys();
                    for (SelectionKey key : selectionKeys) {
                       if (key.isConnectable()) {
                           selectionKeys.clear();
                           finishConnect = true;
                           break;
                       }
                    }
                    selectionKeys.clear();
                }
            }
            success = ch.finishConnect();
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
        closeSelector("read", readSelector);
        closeSelector("write", writeSelector);
        closeSelector("connect", connectSelector);
        ch.close();
    }

    private static void closeSelector(String selectorName, Selector selector) {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn("Failed to close a " + selectorName + " selector.", e);
        }
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    bindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        return unbindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture unbindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            try {
                ch.unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private final class OioSctpChannelConfig extends DefaultSctpChannelConfig {
        private OioSctpChannelConfig(OioSctpChannel channel, SctpChannel javaChannel) {
            super(channel, javaChannel);
        }

        @Override
        protected void autoReadCleared() {
            setReadPending(false);
        }
    }
}
