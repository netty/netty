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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

/**
 * Abstract base class for QUIC codecs.
 */
abstract class QuicheQuicCodec extends ChannelDuplexHandler {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicCodec.class);

    private final Map<ByteBuffer, QuicheQuicChannel> connections = new HashMap<>();
    private final Queue<QuicheQuicChannel> needsFireChannelReadComplete = new ArrayDeque<>();
    private final int maxTokenLength;
    private boolean needsFlush;
    private boolean inChannelRead;

    private ByteBuf versionBuffer;
    private ByteBuf typeBuffer;
    private ByteBuf scidLenBuffer;
    private ByteBuf scidBuffer;
    private ByteBuf dcidLenBuffer;
    private ByteBuf dcidBuffer;
    private ByteBuf tokenBuffer;
    private ByteBuf tokenLenBuffer;

    protected final long config;

    QuicheQuicCodec(long config, int maxTokenLength) {
        this.config = config;
        this.maxTokenLength = maxTokenLength;
    }

    protected QuicheQuicChannel getChannel(ByteBuffer key) {
        return connections.get(key);
    }

    protected void putChannel(QuicheQuicChannel channel) {
        connections.put(channel.key(), channel);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        versionBuffer = allocateNativeOrder(Integer.BYTES);
        typeBuffer = allocateNativeOrder(Byte.BYTES);
        scidBuffer = allocateNativeOrder(Quiche.QUICHE_MAX_CONN_ID_LEN);
        scidLenBuffer = allocateNativeOrder(Integer.BYTES);
        dcidBuffer = allocateNativeOrder(Quiche.QUICHE_MAX_CONN_ID_LEN);
        dcidLenBuffer = allocateNativeOrder(Integer.BYTES);
        tokenLenBuffer = allocateNativeOrder(Integer.BYTES);
        tokenBuffer = allocateNativeOrder(maxTokenLength);
    }

    @SuppressWarnings("deprecation")
    protected static ByteBuf allocateNativeOrder(int capacity) {
        // Just use Unpooled as the life-time of these buffers is long.
        ByteBuf buffer = Unpooled.directBuffer(capacity);

        // As we use the buffers as pointers to int etc we need to ensure we use the right oder so we will
        // see the right value when we read primitive values.
        return PlatformDependent.BIG_ENDIAN_NATIVE_ORDER ? buffer : buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        for (QuicheQuicChannel ch: connections.values()) {
            ch.forceClose();
        }
        connections.clear();

        needsFireChannelReadComplete.clear();

        Quiche.quiche_config_free(config);

        versionBuffer.release();
        scidBuffer.release();
        scidLenBuffer.release();
        dcidBuffer.release();
        dcidLenBuffer.release();
        tokenLenBuffer.release();
        tokenBuffer.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        try {
            ByteBuf buffer = ((DatagramPacket) msg).content();
            if (!buffer.isDirect()) {
                // We need a direct buffer as otherwise we can not access the memoryAddress.
                // Let's do a copy to direct memory.
                ByteBuf direct = ctx.alloc().directBuffer(buffer.readableBytes());
                try {
                    direct.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());
                    channelRead(ctx, packet.sender(), packet.recipient(), direct);
                } finally {
                    direct.release();
                }
            } else {
                channelRead(ctx, packet.sender(), packet.recipient(), buffer);
            }
        } finally {
            packet.release();
        }
    }

    private void channelRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                             InetSocketAddress recipient, ByteBuf buffer) throws Exception {
        long contentAddress = Quiche.memoryAddress(buffer) + buffer.readerIndex();
        int contentReadable = buffer.readableBytes();

        // Ret various len values so quiche_header_info can make use of these.
        scidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
        dcidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
        tokenLenBuffer.setInt(0, maxTokenLength);

        int res = Quiche.quiche_header_info(contentAddress, contentReadable, Quiche.QUICHE_MAX_CONN_ID_LEN,
                Quiche.memoryAddress(versionBuffer), Quiche.memoryAddress(typeBuffer),
                Quiche.memoryAddress(scidBuffer), Quiche.memoryAddress(scidLenBuffer),
                Quiche.memoryAddress(dcidBuffer), Quiche.memoryAddress(dcidLenBuffer),
                Quiche.memoryAddress(tokenBuffer), Quiche.memoryAddress(tokenLenBuffer));
        if (res >= 0) {
            int version = versionBuffer.getInt(0);
            byte type = typeBuffer.getByte(0);
            int scidLen = scidLenBuffer.getInt(0);
            int dcidLen = dcidLenBuffer.getInt(0);
            int tokenLen = tokenLenBuffer.getInt(0);

            QuicheQuicChannel channel = quicPacketRead(ctx, sender, recipient,
                    type, version, scidBuffer.setIndex(0, scidLen),
                    dcidBuffer.setIndex(0, dcidLen), tokenBuffer.setIndex(0, tokenLen));
            if (channel != null) {
                channel.recv(buffer);
                if (channel.markInFireChannelReadCompleteQueue()) {
                    needsFireChannelReadComplete.add(channel);
                }
            }
        } else {
            LOGGER.debug("Unable to parse QUIC header via quiche_header_info: {}", Quiche.errorAsString(res));
        }
    }
    /**
     * Handle a QUIC packet and return {@code true} if we need to call {@link ChannelHandlerContext#flush()}.
     *
     * @param ctx the {@link ChannelHandlerContext}.
     * @param sender the {@link InetSocketAddress} of the sender of the QUIC packet
     * @param recipient the {@link InetSocketAddress} of the recipient of the QUIC packet
     * @param type the type of the packet.
     * @param version the QUIC version
     * @param scid the source connection id.
     * @param dcid the destination connection id
     * @param token the token
     * @return {@code true} if we need to call {@link ChannelHandlerContext#flush()} before there is no new events
     *                      for this handler in the current eventloop run.
     * @throws Exception  thrown if there is an error during processing.
     */
    protected abstract QuicheQuicChannel quicPacketRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                                                        InetSocketAddress recipient, byte type, int version,
                                                        ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception;

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) {
        boolean writeDone = needsFlush;
        needsFlush = false;
        inChannelRead = false;
        for (;;) {
            QuicheQuicChannel channel = needsFireChannelReadComplete.poll();
            if (channel == null) {
                break;
            }
            writeDone |= channel.channelReadComplete();
            if (channel.freeIfClosed()) {
                connections.remove(channel.key());
            }
        }

        if (writeDone) {
            flushNow(ctx);
        }
    }

    @Override
    public final void flush(ChannelHandlerContext ctx) {
        if (inChannelRead) {
            needsFlush = true;
        } else {
            // We can't really easily aggregate flushes, so just do it now.
            flushNow(ctx);
        }
    }

    @Override
    public final void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            boolean writeDone = false;
            Iterator<Map.Entry<ByteBuffer, QuicheQuicChannel>> entries = connections.entrySet().iterator();
            while (entries.hasNext()) {
                QuicheQuicChannel channel = entries.next().getValue();
                // TODO: Be a bit smarter about this.
                writeDone |= channel.writable();
                removeIfClosed(entries, channel);
            }
            if (writeDone) {
                flushNow(ctx);
            }
        } else {
            // As we batch flushes we need to ensure we at least try to flush a batch once the channel becomes
            // unwritable. Otherwise we may end up with buffering too much writes and so waste memory.
            flushNow(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    private static void removeIfClosed(Iterator<?> iterator, QuicheQuicChannel current) {
        if (current.freeIfClosed()) {
            iterator.remove();
        }
    }

    // Reset the flush state and flush the context.
    private void flushNow(ChannelHandlerContext ctx) {
        needsFlush = false;
        ctx.flush();
    }
}
