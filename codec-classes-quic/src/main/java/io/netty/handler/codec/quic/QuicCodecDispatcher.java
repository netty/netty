/*
 * Copyright 2024 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.internal.ObjectUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Special {@link io.netty.channel.ChannelHandler} that should be used to init {@link Channel}s that will be used
 * for QUIC while <a href="https://man7.org/linux/man-pages/man7/socket.7.html">SO_REUSEPORT</a> is used to
 * bind to same {@link java.net.InetSocketAddress} multiple times. This is necessary to ensure QUIC packets are always
 * dispatched to the correct codec that keeps the mapping for the connection id.
 * This implementation use a very simple mapping strategy by encoding the index of the internal datastructure that
 * keeps track of the different {@link ChannelHandlerContext}s into the destination connection id. This way once a
 * {@code QUIC} packet is received its possible to forward it to the right codec.
 * Subclasses might change how encoding / decoding of the index is done by overriding {@link #decodeIndex(ByteBuf)}
 * and {@link #newIdGenerator(int)}.
 * <p>
 * It is important that the same {@link QuicCodecDispatcher} instance is shared between all the {@link Channel}s that
 * are bound to the same {@link java.net.InetSocketAddress} and use {@code SO_REUSEPORT}.
 * <p>
 * An alternative way to handle this would be to do the "routing" to the correct socket in an {@code epbf} program
 * by implementing your own {@link QuicConnectionIdGenerator} that issue ids that can be understood and handled by the
 * {@code epbf} program to route the packet to the correct socket.
 *
 */
public abstract class QuicCodecDispatcher extends ChannelInboundHandlerAdapter {
    // 20 is the max as per RFC.
    // See https://datatracker.ietf.org/doc/html/rfc9000#section-17.2
    private static final int MAX_LOCAL_CONNECTION_ID_LENGTH = 20;

    // Use a CopyOnWriteArrayList as modifications to the List should only happen during bootstrapping and teardown
    // of the channels.
    private final List<ChannelHandlerContextDispatcher> contextList = new CopyOnWriteArrayList<>();
    private final int localConnectionIdLength;

    /**
     * Create a new instance using the default connection id length.
     */
    protected QuicCodecDispatcher() {
        this(MAX_LOCAL_CONNECTION_ID_LENGTH);
    }

    /**
     * Create a new instance
     *
     * @param localConnectionIdLength   the local connection id length. This must be between 10 and 20.
     */
    protected QuicCodecDispatcher(int localConnectionIdLength) {
        // Let's use 10 as a minimum to ensure we still have some bytes left for randomness as we already use
        // 2 of the bytes to encode the index.
        this.localConnectionIdLength = ObjectUtil.checkInRange(localConnectionIdLength,
                10, MAX_LOCAL_CONNECTION_ID_LENGTH, "localConnectionIdLength");
    }

    @Override
    public final boolean isSharable() {
        return true;
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);

        ChannelHandlerContextDispatcher ctxDispatcher = new ChannelHandlerContextDispatcher(ctx);
        contextList.add(ctxDispatcher);
        int idx = contextList.indexOf(ctxDispatcher);
        try {
            QuicConnectionIdGenerator idGenerator = newIdGenerator((short) idx);
            initChannel(ctx.channel(), localConnectionIdLength, idGenerator);
        } catch (Exception e) {
            // Null out on exception and rethrow. We not remove the element as the indices need to be
            // stable.
            contextList.set(idx, null);
            throw e;
        }
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);

        for (int idx = 0; idx < contextList.size(); idx++) {
            ChannelHandlerContextDispatcher ctxDispatcher = contextList.get(idx);
            if (ctxDispatcher != null && ctxDispatcher.ctx.equals(ctx)) {
                // null out, so we can collect the ChannelHandlerContext that was stored in the List.
                contextList.set(idx, null);
                break;
            }
        }
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf connectionId = getDestinationConnectionId(packet.content(), localConnectionIdLength);
        if (connectionId != null) {
            int idx = decodeIndex(connectionId);
            if (contextList.size() > idx) {
                ChannelHandlerContextDispatcher selectedCtx = contextList.get(idx);
                if (selectedCtx != null) {
                    selectedCtx.fireChannelRead(msg);
                    return;
                }
            }
        }
        // We were not be-able to dispatch to a specific ChannelHandlerContext, just forward and let the
        // Quic*Codec handle it directly.
        ctx.fireChannelRead(msg);
    }

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) {
        // Loop over all ChannelHandlerContextDispatchers and ensure fireChannelReadComplete() is called if required.
        // We use and old style for loop as CopyOnWriteArrayList implements RandomAccess and so we can
        // reduce the object creations.
        boolean dispatchForOwnContextAlready = false;
        for (int i = 0; i < contextList.size(); i++) {
            ChannelHandlerContextDispatcher ctxDispatcher = contextList.get(i);
            if (ctxDispatcher != null) {
                boolean fired = ctxDispatcher.fireChannelReadCompleteIfNeeded();
                if (fired && !dispatchForOwnContextAlready) {
                    // Check if we dispatched to ctx so if we didnt at the end we can do it manually.
                    dispatchForOwnContextAlready = ctx.equals(ctxDispatcher.ctx);
                }
            }
        }
        if (!dispatchForOwnContextAlready) {
            ctx.fireChannelReadComplete();
        }
    }

    /**
     * Init the {@link Channel} and add all the needed {@link io.netty.channel.ChannelHandler} to the pipeline.
     * This also included building the {@code QUIC} codec via {@link QuicCodecBuilder} sub-type using the given local
     * connection id length and {@link QuicConnectionIdGenerator}.
     *
     * @param channel                   the {@link Channel} to init.
     * @param localConnectionIdLength   the local connection id length that must be used with the
     *                                  {@link QuicCodecBuilder}.
     * @param idGenerator               the {@link QuicConnectionIdGenerator} that must be used with the
     *                                  {@link QuicCodecBuilder}.
     * @throws Exception                thrown on error.
     */
    protected abstract void initChannel(Channel channel, int localConnectionIdLength,
                                        QuicConnectionIdGenerator idGenerator) throws Exception;

    /**
     * Return the idx that was encoded into the connectionId via the {@link QuicConnectionIdGenerator} before,
     * or {@code -1} if decoding was not successful.
     * <p>
     * Subclasses may override this. In this case {@link #newIdGenerator(int)} should be overridden as well
     * to implement the encoding scheme for the encoding side.
     *
     *
     * @param connectionId  the destination connection id of the {@code QUIC} connection.
     * @return              the index or -1.
     */
    protected int decodeIndex(ByteBuf connectionId) {
        return decodeIdx(connectionId);
    }

    /**
     * Return the destination connection id or {@code null} if decoding was not possible.
     *
     * @param buffer    the buffer
     * @return          the id or {@code null}.
     */
    // Package-private for testing
    @Nullable
    static ByteBuf getDestinationConnectionId(ByteBuf buffer, int localConnectionIdLength) throws QuicException {
        if (buffer.readableBytes() > Byte.BYTES) {
            int offset = buffer.readerIndex();
            boolean shortHeader = hasShortHeader(buffer);
            offset += Byte.BYTES;
            // We are only interested in packets with short header as these the packets that
            // are exchanged after the server did provide the connection id that the client should use.
            if (shortHeader) {
                // See https://www.rfc-editor.org/rfc/rfc9000.html#section-17.3
                // 1-RTT Packet {
                //  Header Form (1) = 0,
                //  Fixed Bit (1) = 1,
                //  Spin Bit (1),
                //  Reserved Bits (2),
                //  Key Phase (1),
                //  Packet Number Length (2),
                //  Destination Connection ID (0..160),
                //  Packet Number (8..32),
                //  Packet Payload (8..),
                //}
                return QuicHeaderParser.sliceCid(buffer, offset, localConnectionIdLength);
            }
        }
        return null;
    }

    // Package-private for testing
    static boolean hasShortHeader(ByteBuf buffer) {
        return QuicHeaderParser.hasShortHeader(buffer.getByte(buffer.readerIndex()));
    }

    // Package-private for testing
    static int decodeIdx(ByteBuf connectionId) {
        if (connectionId.readableBytes() >= 2) {
            return connectionId.getUnsignedShort(connectionId.readerIndex());
        }
        return -1;
    }

    // Package-private for testing
    static ByteBuffer encodeIdx(ByteBuffer buffer, int idx) {
        // Allocate a new buffer and prepend it with the index.
        ByteBuffer b = ByteBuffer.allocate(buffer.capacity() + Short.BYTES);
        // We encode it as unsigned short.
        b.putShort((short) idx).put(buffer).flip();
        return b;
    }

    /**
     * Returns a {@link QuicConnectionIdGenerator} that will encode the given index into all the
     * ids that it produces.
     * <p>
     * Subclasses may override this. In this case {@link #decodeIndex(ByteBuf)} should be overridden as well
     * to implement the encoding scheme for the decoding side.
     *
     * @param idx       the index to encode into each id.
     * @return          the {@link QuicConnectionIdGenerator}.
     */
    protected QuicConnectionIdGenerator newIdGenerator(int idx) {
        return new IndexAwareQuicConnectionIdGenerator(idx, SecureRandomQuicConnectionIdGenerator.INSTANCE);
    }

    private static final class IndexAwareQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
        private final int idx;
        private final QuicConnectionIdGenerator idGenerator;

        IndexAwareQuicConnectionIdGenerator(int idx, QuicConnectionIdGenerator idGenerator) {
            this.idx = idx;
            this.idGenerator = idGenerator;
        }

        @Override
        public ByteBuffer newId(int length) {
            if (length > Short.BYTES) {
                return encodeIdx(idGenerator.newId(length - Short.BYTES), idx);
            }
            return idGenerator.newId(length);
        }

        @Override
        public ByteBuffer newId(ByteBuffer input, int length) {
            if (length > Short.BYTES) {
                return encodeIdx(idGenerator.newId(input, length - Short.BYTES), idx);
            }
            return idGenerator.newId(input, length);
        }

        @Override
        public ByteBuffer newId(ByteBuffer scid, ByteBuffer dcid, int length) {
            if (length > Short.BYTES) {
                return encodeIdx(idGenerator.newId(scid, dcid, length - Short.BYTES), idx);
            }
            return idGenerator.newId(scid, dcid, length);
        }

        @Override
        public int maxConnectionIdLength() {
            return idGenerator.maxConnectionIdLength();
        }

        @Override
        public boolean isIdempotent() {
            // Return false as the id might be different because of the idx that is encoded into it.
            return false;
        }
    }

    private static final class ChannelHandlerContextDispatcher extends AtomicBoolean {

        private final ChannelHandlerContext ctx;

        ChannelHandlerContextDispatcher(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        void fireChannelRead(Object msg) {
            ctx.fireChannelRead(msg);
            set(true);
        }

        boolean fireChannelReadCompleteIfNeeded() {
            if (getAndSet(false)) {
                // There was a fireChannelRead() before, let's call fireChannelReadComplete()
                // so the user is aware that we might be done with the reading loop.
                ctx.fireChannelReadComplete();
                return true;
            }
            return false;
        }
    }
}
