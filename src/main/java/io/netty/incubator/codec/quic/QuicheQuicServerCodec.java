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
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;


import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * {@link QuicheQuicCodec} for QUIC servers.
 */
final class QuicheQuicServerCodec extends QuicheQuicCodec {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicheQuicServerCodec.class);

    private final QuicConnectionIdGenerator connectionIdAddressGenerator;
    private final QuicTokenHandler tokenHandler;
    private final ChannelHandler handler;
    private final Map.Entry<ChannelOption<?>, Object>[] optionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] attrsArray;
    private final ChannelHandler streamHandler;
    private final Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray;
    private final Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray;
    // TODO: Make this configurable ?
    private static final int MAX_LOCAL_CONN_ID = Quiche.QUICHE_MAX_CONN_ID_LEN;
    private ByteBuf mintTokenBuffer;
    private ByteBuf connIdBuffer;

    QuicheQuicServerCodec(long config, QuicTokenHandler tokenHandler,
                          QuicConnectionIdGenerator connectionIdAddressGenerator,
                          ChannelHandler handler,
                          Map.Entry<ChannelOption<?>, Object>[] optionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] attrsArray,
                          ChannelHandler streamHandler,
                          Map.Entry<ChannelOption<?>, Object>[] streamOptionsArray,
                          Map.Entry<AttributeKey<?>, Object>[] streamAttrsArray) {
        super(config, tokenHandler.maxTokenLength());
        this.tokenHandler = tokenHandler;
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
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
        connIdBuffer = allocateNativeOrder(MAX_LOCAL_CONN_ID);
        mintTokenBuffer = allocateNativeOrder(tokenHandler.maxTokenLength());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        super.handlerRemoved(ctx);
        connIdBuffer.release();
        mintTokenBuffer.release();
    }

    @Override
    protected QuicheQuicChannel quicPacketRead(ChannelHandlerContext ctx, InetSocketAddress sender,
                                               InetSocketAddress recipient, byte type, int version,
                                               ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
        ByteBuffer dcidByteBuffer = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        QuicheQuicChannel channel = getChannel(dcidByteBuffer);
        if (channel == null) {
            return handleServer(ctx, sender, type, version, scid, dcid, token);
        }

        return channel;
    }

    private QuicheQuicChannel handleServer(ChannelHandlerContext ctx, InetSocketAddress sender,
                                 @SuppressWarnings("unused") byte type, int version,
                                 ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
        if (!Quiche.quiche_version_is_supported(version)) {
            // Version is not supported, try to negotiate it.
            ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
            int outWriterIndex = out.writerIndex();

            int res = Quiche.quiche_negotiate_version(
                    scid.memoryAddress() + scid.readerIndex(), scid.readableBytes(),
                    dcid.memoryAddress() + dcid.readerIndex(), dcid.readableBytes(),
                    out.memoryAddress() + outWriterIndex, out.writableBytes());
            if (res < 0) {
                out.release();
                Quiche.throwIfError(res);
                return null;
            }

            ctx.writeAndFlush(new DatagramPacket(out.writerIndex(outWriterIndex + res), sender));
        }

        int offset = 0;
        boolean noToken = false;
        if (!token.isReadable()) {
            // Clear buffers so we can reuse these.
            mintTokenBuffer.clear();
            connIdBuffer.clear();

            // The remote peer did not send a token.
            if (tokenHandler.writeToken(mintTokenBuffer, dcid, sender)) {
                ByteBuffer connId = connectionIdAddressGenerator.newId(
                        dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes()), MAX_LOCAL_CONN_ID);
                connIdBuffer.writeBytes(connId);

                ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
                int outWriterIndex = out.writerIndex();
                int written = Quiche.quiche_retry(scid.memoryAddress() + scid.readerIndex(), scid.readableBytes(),
                        dcid.memoryAddress() + dcid.readerIndex(), dcid.readableBytes(),
                        connIdBuffer.memoryAddress() + connIdBuffer.readerIndex(), connIdBuffer.readableBytes(),
                        mintTokenBuffer.memoryAddress() + mintTokenBuffer.readerIndex(),
                        mintTokenBuffer.readableBytes(),
                        version, out.memoryAddress() + outWriterIndex, out.writableBytes());

                if (written < 0) {
                    out.release();
                    Quiche.throwIfError(written);
                } else {
                    ctx.writeAndFlush(new DatagramPacket(out.writerIndex(outWriterIndex + written), sender));
                }
                return null;
            }
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
        if (noToken) {
            conn = Quiche.quiche_accept_no_token(dcid.memoryAddress() + dcid.readerIndex(), MAX_LOCAL_CONN_ID, config);
        } else {
            conn = Quiche.quiche_accept(dcid.memoryAddress() + dcid.readerIndex(), MAX_LOCAL_CONN_ID,
                    token.memoryAddress() + offset, token.readableBytes() - offset, config);
        }
        if (conn < 0) {
            LOGGER.debug("quiche_accept failed");
            return null;
        }

        // Now create the key to store the channel in the map.
        byte[] key = new byte[MAX_LOCAL_CONN_ID];
        dcid.getBytes(dcid.readerIndex(), key);

        QuicheQuicChannel channel = QuicheQuicChannel.forServer(
                ctx.channel(), ByteBuffer.wrap(key), conn, Quiche.traceId(conn, dcid), sender,
                streamHandler, streamOptionsArray, streamAttrsArray);
        Quic.setupChannel(channel, optionsArray, attrsArray, handler, LOGGER);
        putChannel(channel);
        ctx.channel().eventLoop().register(channel);
        return channel;
    }
}
