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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.ssl.SniCompletionEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;

import static io.netty.incubator.codec.quic.Quiche.allocateNativeOrder;

/**
 * {@link QuicheQuicCodec} for QUIC servers.
 */
final class QuicheQuicServerCodec extends QuicheQuicCodec {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicServerCodec.class);

    private final Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider;
    private final QuicConnectionIdGenerator connectionIdAddressGenerator;
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
                          FlushStrategy flushStrategy,
                          Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                          ChannelHandler handler,
                          Map.Entry<ChannelOption<?>, Object>[] optionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] attrsArray,
                          ChannelHandler streamHandler,
                          Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        super(config, localConnIdLength, tokenHandler.maxTokenLength(), flushStrategy);
        this.tokenHandler = tokenHandler;
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        this.sslEngineProvider = sslEngineProvider;
        this.handler = handler;
        this.optionsArray = optionsArray;
        this.attrsArray = attrsArray;
        this.streamHandler = streamHandler;
        this.streamOptionsArray = streamOptionsArray;
        this.streamAttrsArray = streamAttrsArray;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        super.handlerAdded(ctx);
        connIdBuffer = Quiche.allocateNativeOrder(localConnIdLength);
        mintTokenBuffer = allocateNativeOrder(tokenHandler.maxTokenLength());
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
    protected QuicheQuicChannel quicPacketRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                                               InetSocketAddress recipient, QuicPacketType type, int version,
                                               ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
        ByteBuffer dcidByteBuffer = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        QuicheQuicChannel channel = getChannel(dcidByteBuffer);
        if (channel == null) {
            return handleServer(ctx, sender, type, version, scid, dcid, token);
        }

        return channel;
    }

    private QuicheQuicChannel handleServer(ChannelHandlerContext ctx, InetSocketAddress sender,
                                 @SuppressWarnings("unused") QuicPacketType type, int version,
                                 ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
        if (!Quiche.quiche_version_is_supported(version)) {
            // Version is not supported, try to negotiate it.
            ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
            int outWriterIndex = out.writerIndex();

            int res = Quiche.quiche_negotiate_version(
                    Quiche.memoryAddress(scid) + scid.readerIndex(), scid.readableBytes(),
                    Quiche.memoryAddress(dcid) + dcid.readerIndex(), dcid.readableBytes(),
                    Quiche.memoryAddress(out) + outWriterIndex, out.writableBytes());
            if (res < 0) {
                out.release();
                Quiche.throwIfError(res);
            } else {
                ctx.writeAndFlush(new DatagramPacket(out.writerIndex(outWriterIndex + res), sender));
            }
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
                        dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes()), localConnIdLength);
                connIdBuffer.writeBytes(connId);

                ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
                int outWriterIndex = out.writerIndex();
                int written = Quiche.quiche_retry(Quiche.memoryAddress(scid) + scid.readerIndex(), scid.readableBytes(),
                        Quiche.memoryAddress(dcid) + dcid.readerIndex(), dcid.readableBytes(),
                        Quiche.memoryAddress(connIdBuffer) + connIdBuffer.readerIndex(), connIdBuffer.readableBytes(),
                        Quiche.memoryAddress(mintTokenBuffer) + mintTokenBuffer.readerIndex(),
                        mintTokenBuffer.readableBytes(),
                        version, Quiche.memoryAddress(out) + outWriterIndex, out.writableBytes());

                if (written < 0) {
                    out.release();
                    Quiche.throwIfError(written);
                } else {
                    ctx.writeAndFlush(new DatagramPacket(out.writerIndex(outWriterIndex + written), sender));
                }
                return null;
            }
            offset = 0;
            noToken = true;
        } else {
            offset = tokenHandler.validateToken(token, sender);
            if (offset == -1) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("invalid token: {}", token.toString(CharsetUtil.US_ASCII));
                }
                return null;
            }
        }

        final long conn;
        final ByteBuffer key;
        final long scidAddr;
        final int scidLen;
        final long ocidAddr;
        final int ocidLen;

        if (noToken) {
            connIdBuffer.clear();
            key = connectionIdAddressGenerator.newId(
                    dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes()), localConnIdLength);
            connIdBuffer.writeBytes(key.duplicate());
            scidAddr = Quiche.memoryAddress(connIdBuffer) + connIdBuffer.readerIndex();
            scidLen = localConnIdLength;
            ocidAddr = -1;
            ocidLen = -1;
        } else {
            scidAddr = Quiche.memoryAddress(dcid) + dcid.readerIndex();
            scidLen = localConnIdLength;
            ocidAddr = Quiche.memoryAddress(token) + offset;
            ocidLen = token.readableBytes() - offset;
            // Now create the key to store the channel in the map.
            byte[] bytes = new byte[localConnIdLength];
            dcid.getBytes(dcid.readerIndex(), bytes);
            key = ByteBuffer.wrap(bytes);
        }

        QuicheQuicChannel channel = QuicheQuicChannel.forServer(
                ctx.channel(), key, sender, config.isDatagramSupported(),
                streamHandler, streamOptionsArray, streamAttrsArray);
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
            ByteBuffer peerAddrMemory = sockaddrMemory.internalNioBuffer(0, sockaddrMemory.capacity());
            int peerLen = SockaddrIn.setAddress(peerAddrMemory, sender);
            return Quiche.quiche_conn_new_with_tls(scidAddr, scidLen, ocidAddr, ocidLen,
                    Quiche.memoryAddressWithPosition(peerAddrMemory), peerLen,
                    config.nativeAddress(), ssl, true);
        });
        if (connection  == null) {
            channel.unsafe().closeForcibly();
            LOGGER.debug("quiche_accept failed");
            return null;
        }

        channel.attachQuicheConnection(connection);

        putChannel(channel);
        ctx.channel().eventLoop().register(channel);
        channel.pipeline().fireUserEventTriggered(new QuicConnectionEvent(null, sender));
        return channel;
    }
}
