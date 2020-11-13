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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Abstract base class for QUIC codecs.
 */
public abstract class QuicCodec extends ChannelDuplexHandler {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicCodec.class);

    private final Map<ByteBuffer, QuicheQuicChannel> connections = new HashMap<>();
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

    QuicCodec(long config, int maxTokenLength) {
        this.config = config;
        this.maxTokenLength = maxTokenLength;
    }

    protected QuicheQuicChannel getChannel(ByteBuffer key) {
        return connections.get(key);
    }

    protected void putChannel(ByteBuffer key, QuicheQuicChannel channel) {
        connections.put(key, channel);
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
            long contentAddress = buffer.memoryAddress() + buffer.readerIndex();
            int contentReadable = buffer.readableBytes();

            // Ret various len values so quiche_header_info can make use of these.
            scidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
            dcidLenBuffer.setInt(0, Quiche.QUICHE_MAX_CONN_ID_LEN);
            tokenLenBuffer.setInt(0, maxTokenLength);

            int res = Quiche.quiche_header_info(contentAddress, contentReadable, Quiche.QUICHE_MAX_CONN_ID_LEN,
                    versionBuffer.memoryAddress(), typeBuffer.memoryAddress(),
                    scidBuffer.memoryAddress(), scidLenBuffer.memoryAddress(),
                    dcidBuffer.memoryAddress(), dcidLenBuffer.memoryAddress(),
                    tokenBuffer.memoryAddress(), tokenLenBuffer.memoryAddress());
            if (res >= 0) {
                int version = versionBuffer.getInt(0);
                byte type = typeBuffer.getByte(0);
                int scidLen = scidLenBuffer.getInt(0);
                int dcidLen = dcidLenBuffer.getInt(0);
                int tokenLen = tokenLenBuffer.getInt(0);

                needsFlush |= quicPacketRead(ctx, packet, type, version, scidBuffer.setIndex(0, scidLen),
                        dcidBuffer.setIndex(0, dcidLen), tokenBuffer.setIndex(0, tokenLen));
            } else {
                LOGGER.debug("Unable to parse QUIC header via quiche_header_info: {}", Quiche.errorAsString(res));
            }
        } finally {
            packet.release();
        }
    }

    /**
     * Handle a QUIC packet and return {@code true} if we need to call {@link ChannelHandlerContext#flush()}.
     *
     * @param ctx the {@link ChannelHandlerContext}.
     * @param packet the {@link DatagramPacket} tat contains the QUIC packet.
     * @param type the type of the packet.
     * @param version the QUIC version
     * @param scid the source connection id.
     * @param dcid the destination connection id
     * @param token the token
     * @return {@code true} if we need to call {@link ChannelHandlerContext#flush()} before there is no new events
     *                      for this handler in the current eventloop run.
     * @throws Exception  thrown if there is an error during processing.
     */
    protected abstract boolean quicPacketRead(ChannelHandlerContext ctx, DatagramPacket packet, byte type, int version,
                                                    ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception;

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) {
        boolean writeDone = needsFlush;
        needsFlush = false;
        inChannelRead = false;
        Iterator<Map.Entry<ByteBuffer, QuicheQuicChannel>> entries = connections.entrySet().iterator();
        while (entries.hasNext()) {
            QuicheQuicChannel channel = entries.next().getValue();
            // TODO: Be a bit smarter about this.
            writeDone |= channel.channelReadComplete();

            removeIfClosed(entries, channel);
        }
        if (writeDone) {
            ctx.flush();
        }
    }

    @Override
    public final void flush(ChannelHandlerContext ctx) {
        if (inChannelRead) {
            needsFlush = true;
        } else {
            // We can't really easily aggregate flushes, so just do it now.
            ctx.flush();
        }
    }

    @Override
    public final void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
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
                ctx.flush();
            }
        }
        ctx.fireChannelWritabilityChanged();
    }

    private static void removeIfClosed(Iterator<?> iterator, QuicheQuicChannel current) {
        if (current.freeIfClosed()) {
            iterator.remove();
        }
    }
}
