/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import static io.netty.handler.codec.quic.Quiche.allocateNativeOrder;

/**
 * Abstract base class for QUIC codecs.
 */
abstract class QuicheQuicCodec extends ChannelDuplexHandler {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicCodec.class);
    private final ConnectionIdChannelMap connectionIdToChannel = new ConnectionIdChannelMap();
    private final Set<QuicheQuicChannel> channels = new HashSet<>();
    private final Queue<QuicheQuicChannel> needsFireChannelReadComplete = new ArrayDeque<>();
    private final Queue<QuicheQuicChannel> delayedRemoval = new ArrayDeque<>();

    private final Consumer<QuicheQuicChannel> freeTask = this::removeChannel;
    private final FlushStrategy flushStrategy;
    private final int localConnIdLength;
    private final QuicheConfig config;

    private MessageSizeEstimator.Handle estimatorHandle;
    private QuicHeaderParser headerParser;
    private QuicHeaderParser.QuicHeaderProcessor parserCallback;
    private int pendingBytes;
    private int pendingPackets;
    private boolean inChannelReadComplete;
    private boolean delayRemoval;

    // This buffer is used to copy InetSocketAddress to sockaddr_storage and so pass it down the JNI layer.
    private ByteBuf senderSockaddrMemory;
    private ByteBuf recipientSockaddrMemory;

    QuicheQuicCodec(QuicheConfig config, int localConnIdLength, FlushStrategy flushStrategy) {
        this.config = config;
        this.localConnIdLength = localConnIdLength;
        this.flushStrategy = flushStrategy;
    }

    @Override
    public final boolean isSharable() {
        return false;
    }

    @Nullable
    protected final QuicheQuicChannel getChannel(ByteBuffer key) {
        return connectionIdToChannel.get(key);
    }

    private void addMapping(QuicheQuicChannel channel, ByteBuffer id) {
        QuicheQuicChannel ch = connectionIdToChannel.put(id, channel);
        assert ch == null;
    }

    private void removeMapping(QuicheQuicChannel channel, ByteBuffer id) {
        QuicheQuicChannel ch = connectionIdToChannel.remove(id);
        assert ch == channel;
    }

    private void processDelayedRemoval() {
        for (;;) {
            // Now remove all channels that we marked for removal.
            QuicheQuicChannel toBeRemoved = delayedRemoval.poll();
            if (toBeRemoved == null) {
                break;
            }
            removeChannel(toBeRemoved);
        }
    }

    private void removeChannel(QuicheQuicChannel channel) {
        if (delayRemoval) {
            boolean added = delayedRemoval.offer(channel);
            assert added;
        } else {
            boolean removed = channels.remove(channel);
            if (removed) {
                for (ByteBuffer id : channel.sourceConnectionIds()) {
                    QuicheQuicChannel ch = connectionIdToChannel.remove(id);
                    assert ch == channel;
                }
            }
        }
    }

    protected final void addChannel(QuicheQuicChannel channel) {
        boolean added = channels.add(channel);
        assert added;
        for (ByteBuffer id : channel.sourceConnectionIds()) {
            QuicheQuicChannel ch = connectionIdToChannel.put(id.duplicate(), channel);
            assert ch == null;
        }
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) {
        senderSockaddrMemory = allocateNativeOrder(Quiche.SIZEOF_SOCKADDR_STORAGE);
        recipientSockaddrMemory = allocateNativeOrder(Quiche.SIZEOF_SOCKADDR_STORAGE);
        headerParser = new QuicHeaderParser(localConnIdLength);
        parserCallback = new QuicCodecHeaderProcessor(ctx);
        estimatorHandle = ctx.channel().config().getMessageSizeEstimator().newHandle();
        handlerAdded(ctx, localConnIdLength);
    }

    /**
     * See {@link io.netty.channel.ChannelHandler#handlerAdded(ChannelHandlerContext)}.
     */
    protected void handlerAdded(ChannelHandlerContext ctx, int localConnIdLength) {
        // NOOP.
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        try {
            // Use a copy of the array as closing the channel may cause an unwritable event that could also
            // remove channels.
            for (QuicheQuicChannel ch : channels.toArray(new QuicheQuicChannel[0])) {
                ch.forceClose();
            }
            if (pendingPackets > 0) {
                flushNow(ctx);
            }
        } finally {
            channels.clear();
            connectionIdToChannel.clear();
            needsFireChannelReadComplete.clear();
            delayedRemoval.clear();

            config.free();
            if (senderSockaddrMemory != null) {
                senderSockaddrMemory.release();
            }
            if (recipientSockaddrMemory != null) {
                recipientSockaddrMemory.release();
            }
            if (headerParser != null) {
                headerParser.close();
                headerParser = null;
            }
        }
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        try {
            ByteBuf buffer = ((DatagramPacket) msg).content();
            if (!buffer.isDirect()) {
                // We need a direct buffer as otherwise we can not access the memoryAddress.
                // Let's do a copy to direct memory.
                ByteBuf direct = ctx.alloc().directBuffer(buffer.readableBytes());
                try {
                    direct.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    handleQuicPacket(packet.sender(), packet.recipient(), direct);
                } finally {
                    direct.release();
                }
            } else {
                handleQuicPacket(packet.sender(), packet.recipient(), buffer);
            }
        } finally {
            packet.release();
        }
    }

    private void handleQuicPacket(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buffer) {
        try {
            headerParser.parse(sender, recipient, buffer, parserCallback);
        } catch (Exception e) {
            LOGGER.debug("Error while processing QUIC packet", e);
        }
    }

    /**
     * Handle a QUIC packet and return {@link QuicheQuicChannel} that is mapped to the id.
     *
     * @param ctx the {@link ChannelHandlerContext}.
     * @param sender the {@link InetSocketAddress} of the sender of the QUIC packet
     * @param recipient the {@link InetSocketAddress} of the recipient of the QUIC packet
     * @param type the type of the packet.
     * @param version the QUIC version
     * @param scid the source connection id.
     * @param dcid the destination connection id
     * @param token the token
     * @param senderSockaddrMemory the {@link ByteBuf} that can be used for the sender {@code struct sockaddr).
     * @param recipientSockaddrMemory the {@link ByteBuf} that can be used for the recipient {@code struct sockaddr).
     * @param freeTask the {@link Consumer} that will be called once native memory of the {@link QuicheQuicChannel} is
     *                  freed and so the mappings should be deleted to the ids.
     * @param localConnIdLength the length of the local connection ids.
     * @param config the {@link QuicheConfig} that is used.
     * @return the {@link QuicheQuicChannel} that is mapped to the id.
     * @throws Exception  thrown if there is an error during processing.
     */
    @Nullable
    protected abstract QuicheQuicChannel quicPacketRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                                                        InetSocketAddress recipient, QuicPacketType type, long version,
                                                        ByteBuf scid, ByteBuf dcid, ByteBuf token,
                                                        ByteBuf senderSockaddrMemory, ByteBuf recipientSockaddrMemory,
                                                        Consumer<QuicheQuicChannel> freeTask,
                                                        int localConnIdLength, QuicheConfig config) throws Exception;

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) {
        inChannelReadComplete = true;
        try {
            for (;;) {
                QuicheQuicChannel channel = needsFireChannelReadComplete.poll();
                if (channel == null) {
                    break;
                }
                channel.recvComplete();
            }
        } finally {
            inChannelReadComplete = false;
            if (pendingPackets > 0) {
                flushNow(ctx);
            }
        }
    }

    @Override
    public final void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            // Ensure we delay removal from the channels Set as otherwise we will might see an exception
            // due modifications while iteration.
            delayRemoval = true;
            try {
                for (QuicheQuicChannel channel : channels) {
                    // TODO: Be a bit smarter about this.
                    channel.writable();
                }
            } finally {
                // We are done with the loop, reset the flag and process the removals from the channels Set.
                delayRemoval = false;
                processDelayedRemoval();
            }
        } else {
            // As we batch flushes we need to ensure we at least try to flush a batch once the channel becomes
            // unwritable. Otherwise we may end up with buffering too much writes and so waste memory.
            ctx.flush();
        }

        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)  {
        pendingPackets ++;
        int size = estimatorHandle.size(msg);
        if (size > 0) {
            pendingBytes += size;
        }
        try {
            ctx.write(msg, promise);
        } finally {
            flushIfNeeded(ctx);
        }
    }

    @Override
    public final void flush(ChannelHandlerContext ctx) {
        // If we are in the channelReadComplete(...) method we might be able to delay the flush(...) until we finish
        // processing all channels.
        if (inChannelReadComplete) {
            flushIfNeeded(ctx);
        } else if (pendingPackets > 0) {
            flushNow(ctx);
        }
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        if (remoteAddress instanceof QuicheQuicChannelAddress) {
            QuicheQuicChannelAddress addr = (QuicheQuicChannelAddress) remoteAddress;
            QuicheQuicChannel channel = addr.channel;
            connectQuicChannel(channel, remoteAddress, localAddress,
                    senderSockaddrMemory, recipientSockaddrMemory, freeTask, localConnIdLength, config, promise);
        } else {
            ctx.connect(remoteAddress, localAddress, promise);
        }
    }

    /**
     * Connects the given {@link QuicheQuicChannel}.
     *
     * @param channel                   the {@link QuicheQuicChannel} to connect.
     * @param remoteAddress             the remote {@link SocketAddress}.
     * @param localAddress              the local  {@link SocketAddress}
     * @param senderSockaddrMemory      the {@link ByteBuf} that can be used for the sender {@code struct sockaddr).
     * @param recipientSockaddrMemory   the {@link ByteBuf} that can be used for the recipient {@code struct sockaddr).
     * @param freeTask                  the {@link Consumer} that will be called once native memory of the
     *                                  {@link QuicheQuicChannel} is freed and so the mappings should be deleted to
     *                                  the ids.
     * @param localConnIdLength         the length of the local connection ids.
     * @param config                    the {@link QuicheConfig} that is used.
     * @param promise                   the {@link ChannelPromise} to notify once the connect is done.
     */
    protected abstract void connectQuicChannel(QuicheQuicChannel channel, SocketAddress remoteAddress,
                                               SocketAddress localAddress, ByteBuf senderSockaddrMemory,
                                               ByteBuf recipientSockaddrMemory, Consumer<QuicheQuicChannel> freeTask,
                                               int localConnIdLength, QuicheConfig config, ChannelPromise promise);

    private void flushIfNeeded(ChannelHandlerContext ctx) {
        // Check if we should force a flush() and so ensure the packets are delivered in a timely
        // manner and also make room in the outboundbuffer again that belongs to the underlying channel.
        if (flushStrategy.shouldFlushNow(pendingPackets, pendingBytes)) {
            flushNow(ctx);
        }
    }

    private void flushNow(ChannelHandlerContext ctx) {
        pendingBytes = 0;
        pendingPackets = 0;
        ctx.flush();
    }

    private final class QuicCodecHeaderProcessor implements QuicHeaderParser.QuicHeaderProcessor {

        private final ChannelHandlerContext ctx;

        QuicCodecHeaderProcessor(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void process(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buffer, QuicPacketType type,
                            long version, ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
            QuicheQuicChannel channel = quicPacketRead(ctx, sender, recipient,
                    type, version, scid,
                    dcid, token, senderSockaddrMemory, recipientSockaddrMemory, freeTask, localConnIdLength, config);
            if (channel != null) {
                // Add to queue first, we might be able to safe some flushes and consolidate them
                // in channelReadComplete(...) this way.
                if (channel.markInFireChannelReadCompleteQueue()) {
                    needsFireChannelReadComplete.add(channel);
                }
                channel.recv(sender, recipient, buffer);
                for (ByteBuffer retiredSourceConnectionId : channel.retiredSourceConnectionId()) {
                    removeMapping(channel, retiredSourceConnectionId);
                }
                for (ByteBuffer newSourceConnectionId : channel.newSourceConnectionIds()) {
                    addMapping(channel, newSourceConnectionId);
                }
            }
        }
    }
}
