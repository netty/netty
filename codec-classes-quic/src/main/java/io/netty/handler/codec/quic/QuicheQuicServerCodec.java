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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link QuicheQuicCodec} for QUIC servers.
 */
final class QuicheQuicServerCodec extends QuicheQuicCodec {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicServerCodec.class);
    private final Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider;
    private final Executor sslTaskExecutor;
    private final QuicConnectionIdGenerator connectionIdAddressGenerator;
    private final QuicResetTokenGenerator resetTokenGenerator;
    private final QuicTokenHandler tokenHandler;
    private final ChannelHandler handler;
    private final Map.Entry<ChannelOption<?>, Object>[] optionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] attrsArray;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    private ByteBuf mintTokenBuffer;
    private ByteBuf connIdBuffer;

    QuicheQuicServerCodec(QuicheConfig config,
                          int localConnIdLength,
                          QuicTokenHandler tokenHandler,
                          QuicConnectionIdGenerator connectionIdAddressGenerator,
                          QuicResetTokenGenerator resetTokenGenerator,
                          FlushStrategy flushStrategy,
                          Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                          Executor sslTaskExecutor,
                          ChannelHandler handler,
                          Map.Entry<ChannelOption<?>, Object>[] optionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] attrsArray,
                          ChannelHandler streamHandler,
                          Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        super(config, localConnIdLength, flushStrategy);
        this.tokenHandler = tokenHandler;
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        this.resetTokenGenerator = resetTokenGenerator;
        this.sslEngineProvider = sslEngineProvider;
        this.sslTaskExecutor = sslTaskExecutor;
        this.handler = handler;
        this.optionsArray = optionsArray;
        this.attrsArray = attrsArray;
        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
    }

    @Override
    protected void handlerAdded(ChannelHandlerContext ctx, int localConnIdLength) {
        connIdBuffer = Quiche.allocateNativeOrder(localConnIdLength);
        mintTokenBuffer = Unpooled.directBuffer(tokenHandler.maxTokenLength());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        super.handlerRemoved(ctx);
        if (connIdBuffer != null) {
            connIdBuffer.release();
        }
        if (mintTokenBuffer != null) {
            mintTokenBuffer.release();
        }
    }

    @Override
    @Nullable
    protected QuicheQuicChannel quicPacketRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                                               InetSocketAddress recipient, QuicPacketType type, long version,
                                               ByteBuf scid, ByteBuf dcid, ByteBuf token,
                                               ByteBuf senderSockaddrMemory, ByteBuf recipientSockaddrMemory,
                                               Consumer<QuicheQuicChannel> freeTask, int localConnIdLength,
                                               QuicheConfig config)
            throws Exception {
        ByteBuffer dcidByteBuffer = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        QuicheQuicChannel channel = getChannel(dcidByteBuffer);
        if (channel == null && type == QuicPacketType.INITIAL) {
            // We only want to possibility create a new QuicChannel if this is the initial packet, otherwise
            // drop the packet on the floor if we did not find a mapping before.
            return handleServer(ctx, sender, recipient, type, version, scid, dcid, token,
                    senderSockaddrMemory, recipientSockaddrMemory, freeTask, localConnIdLength, config);
        }
        return channel;
    }

    private static void writePacket(ChannelHandlerContext ctx, int res, ByteBuf buffer, InetSocketAddress sender)
            throws Exception {
        if (res < 0) {
            buffer.release();
            if (res != Quiche.QUICHE_ERR_DONE) {
                throw Quiche.convertToException(res);
            }
        } else {
            ctx.writeAndFlush(new DatagramPacket(buffer.writerIndex(buffer.writerIndex() + res), sender));
        }
    }

    @Nullable
    private QuicheQuicChannel handleServer(ChannelHandlerContext ctx, InetSocketAddress sender,
                                           InetSocketAddress recipient,
                                           @SuppressWarnings("unused") QuicPacketType type, long version,
                                           ByteBuf scid, ByteBuf dcid, ByteBuf token,
                                           ByteBuf senderSockaddrMemory, ByteBuf recipientSockaddrMemory,
                                           Consumer<QuicheQuicChannel> freeTask, int localConnIdLength,
                                           QuicheConfig config) throws Exception {
        // Version is an unsigned int.
        if (!Quiche.quiche_version_is_supported((int) version)) {
            // Version is not supported, try to negotiate it.
            ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);

            int res = Quiche.quiche_negotiate_version(
                    Quiche.readerMemoryAddress(scid), scid.readableBytes(),
                    Quiche.readerMemoryAddress(dcid), dcid.readableBytes(),
                    Quiche.writerMemoryAddress(out), out.writableBytes());
            writePacket(ctx, res, out, sender);
            return null;
        }

        final int offset;
        boolean noToken = false;
        if (!token.isReadable()) {
            // Clear buffers so we can reuse these.
            mintTokenBuffer.clear();
            connIdBuffer.clear();

            // The remote peer did not send a token.
            if (tokenHandler.writeToken(mintTokenBuffer, dcid, sender)) {
                ByteBuffer connId = connectionIdAddressGenerator.newId(
                        scid.internalNioBuffer(scid.readerIndex(), scid.readableBytes()),
                        dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes()),
                        localConnIdLength);
                connIdBuffer.writeBytes(connId);

                ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
                int written = Quiche.quiche_retry(
                        Quiche.readerMemoryAddress(scid), scid.readableBytes(),
                        Quiche.readerMemoryAddress(dcid), dcid.readableBytes(),
                        Quiche.readerMemoryAddress(connIdBuffer), connIdBuffer.readableBytes(),
                        Quiche.readerMemoryAddress(mintTokenBuffer), mintTokenBuffer.readableBytes(),
                        // unsigned int.
                        (int) version,
                        Quiche.writerMemoryAddress(out), out.writableBytes());

                writePacket(ctx, written, out, sender);
                return null;
            }
            offset = 0;
            noToken = true;
        } else {
            // Slice the token before pass it ot the QuicTokenHandler as the implementation might modify
            // the readerIndex.
            // See https://github.com/netty/netty-incubator-codec-quic/issues/742
            offset = tokenHandler.validateToken(token.slice(), sender);
            if (offset == -1) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("invalid token: {}", token.toString(CharsetUtil.US_ASCII));
                }
                return null;
            }
        }

        final ByteBuffer key;
        final long scidAddr;
        final int scidLen;
        final long ocidAddr;
        final int ocidLen;

        if (noToken) {
            connIdBuffer.clear();
            key = connectionIdAddressGenerator.newId(
                    scid.internalNioBuffer(scid.readerIndex(), scid.readableBytes()),
                    dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes()),
                    localConnIdLength);
            connIdBuffer.writeBytes(key.duplicate());
            scidAddr = Quiche.readerMemoryAddress(connIdBuffer);
            scidLen = localConnIdLength;
            ocidAddr = -1;
            ocidLen = -1;

            QuicheQuicChannel existingChannel = getChannel(key);
            if (existingChannel != null) {
                return existingChannel;
            }
        } else {
            scidAddr = Quiche.readerMemoryAddress(dcid);
            scidLen = localConnIdLength;
            ocidLen = token.readableBytes() - offset;
            ocidAddr = Quiche.memoryAddress(token, offset, ocidLen);
            // Now create the key to store the channel in the map.
            byte[] bytes = new byte[localConnIdLength];
            dcid.getBytes(dcid.readerIndex(), bytes);
            key = ByteBuffer.wrap(bytes);
        }
        QuicheQuicChannel channel = QuicheQuicChannel.forServer(
                ctx.channel(), key, recipient, sender, config.isDatagramSupported(),
                streamHandler, streamOptionsArray, streamAttrsArray, freeTask, sslTaskExecutor,
                connectionIdAddressGenerator, resetTokenGenerator);

        // We also need to add the original id as there might be multiple INITIAL packets.
        byte[] originalId = new byte[dcid.readableBytes()];
        dcid.getBytes(dcid.readerIndex(), originalId);
        channel.sourceConnectionIds().add(ByteBuffer.wrap(originalId));

        Quic.setupChannel(channel, optionsArray, attrsArray, handler, LOGGER);
        QuicSslEngine engine = sslEngineProvider.apply(channel);
        if (!(engine instanceof QuicheQuicSslEngine)) {
            channel.unsafe().closeForcibly();
            throw new IllegalArgumentException("QuicSslEngine is not of type "
                    + QuicheQuicSslEngine.class.getSimpleName());
        }
        if (engine.getUseClientMode()) {
            channel.unsafe().closeForcibly();
            throw new IllegalArgumentException("QuicSslEngine is not created in server mode");
        }

        QuicheQuicSslEngine quicSslEngine = (QuicheQuicSslEngine) engine;
        QuicheQuicConnection connection = quicSslEngine.createConnection(ssl -> {
            ByteBuffer localAddrMemory =
                    recipientSockaddrMemory.internalNioBuffer(0, recipientSockaddrMemory.capacity());
            int localLen = SockaddrIn.setAddress(localAddrMemory, recipient);

            ByteBuffer peerAddrMemory = senderSockaddrMemory.internalNioBuffer(0, senderSockaddrMemory.capacity());
            int peerLen = SockaddrIn.setAddress(peerAddrMemory, sender);
            return Quiche.quiche_conn_new_with_tls(scidAddr, scidLen, ocidAddr, ocidLen,
                    Quiche.memoryAddressWithPosition(localAddrMemory), localLen,
                    Quiche.memoryAddressWithPosition(peerAddrMemory), peerLen,
                    config.nativeAddress(), ssl, true);
        });
        if (connection  == null) {
            channel.unsafe().closeForcibly();
            LOGGER.debug("quiche_accept failed");
            return null;
        }

        channel.attachQuicheConnection(connection);

        addChannel(channel);

        ctx.channel().eventLoop().register(channel);
        return channel;
    }

    @Override
    protected void connectQuicChannel(QuicheQuicChannel channel, SocketAddress remoteAddress,
                                      SocketAddress localAddress, ByteBuf senderSockaddrMemory,
                                      ByteBuf recipientSockaddrMemory, Consumer<QuicheQuicChannel> freeTask,
                                      int localConnIdLength, QuicheConfig config, ChannelPromise promise) {
        promise.setFailure(new UnsupportedOperationException());
    }
}
