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
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;


import java.nio.ByteBuffer;

/**
 * {@link QuicCodec} for QUIC servers.
 */
public final class QuicServerCodec extends QuicCodec {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicServerCodec.class);

    private final ChannelHandler quicChannelHandler;
    private final QuicConnectionIdAddressGenerator connectionIdAddressGenerator;
    private final QuicTokenHandler tokenHandler;
    // TODO: Make this configurable ?
    private static final int MAX_LOCAL_CONN_ID = Quiche.QUICHE_MAX_CONN_ID_LEN;
    private ByteBuf mintTokenBuffer;
    private ByteBuf connIdBuffer;

    QuicServerCodec(long config, QuicTokenHandler tokenHandler,
                    QuicConnectionIdAddressGenerator connectionIdAddressGenerator, ChannelHandler quicChannelHandler) {
        super(config, tokenHandler.maxTokenLength());
        this.tokenHandler = tokenHandler;
        this.connectionIdAddressGenerator = connectionIdAddressGenerator;
        this.quicChannelHandler = quicChannelHandler;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        super.handlerAdded(ctx);
        connIdBuffer = allocateNativeOrder(MAX_LOCAL_CONN_ID);
        mintTokenBuffer = allocateNativeOrder(tokenHandler.maxTokenLength());
    }

    @Override
    protected boolean quicPacketRead(ChannelHandlerContext ctx, DatagramPacket packet, byte type, int version,
                                     ByteBuf scid, ByteBuf dcid, ByteBuf token) throws Exception {
        ByteBuffer dcidByteBuffer = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        QuicheQuicChannel channel = getChannel(dcidByteBuffer);
        if (channel == null) {
            return handleServer(ctx, packet, type, version, scid, dcid, token);
        }

        channel.recv(packet.content());
        return false;
    }

    private boolean handleServer(ChannelHandlerContext ctx, DatagramPacket packet,
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
                return false;
            }

            ctx.write(new DatagramPacket(out.writerIndex(outWriterIndex + res), packet.sender()));
            return true;
        }

        if (!token.isReadable()) {
            // Clear buffers so we can reuse these.
            mintTokenBuffer.clear();
            connIdBuffer.clear();

            // The remote peer did not send a token.
            tokenHandler.writeToken(mintTokenBuffer, dcid, packet.sender());

            QuicConnectionIdAddress connId = connectionIdAddressGenerator.newAddress(dcid,
                    MAX_LOCAL_CONN_ID);
            connIdBuffer.writeBytes(connId.connId);

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
                ctx.write(new DatagramPacket(out.writerIndex(outWriterIndex + written),
                        packet.sender()));
                return true;
            }
            return false;
        }
        int offset = tokenHandler.validateToken(token, packet.sender());
        if (offset == -1) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("invalid token: {}", token.toString(CharsetUtil.US_ASCII));
            }
            return false;
        }

        long conn = Quiche.quiche_accept(dcid.memoryAddress() + dcid.readerIndex(), MAX_LOCAL_CONN_ID,
                token.memoryAddress() + offset, token.readableBytes() - offset, config);
        if (conn < 0) {
            LOGGER.debug("quiche_accept failed");
            return false;
        }

        QuicheQuicChannel channel = QuicheQuicChannel.forServer(
                ctx.channel(), conn, Quiche.traceId(conn, dcid), packet.sender());
        channel.pipeline().addLast(quicChannelHandler);

        // Now create the key to store the channel in the map.
        byte[] key = new byte[Quiche.QUICHE_MAX_CONN_ID_LEN];
        dcid.getBytes(dcid.readerIndex(), key);
        putChannel(ByteBuffer.wrap(key), channel);

        ctx.channel().eventLoop().register(channel);
        channel.recv(packet.content());
        return false;
    }
}
