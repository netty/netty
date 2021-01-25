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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * {@link QuicheQuicCodec} for QUIC clients.
 */
final class QuicheQuicClientCodec extends QuicheQuicCodec {

    private final Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider;

    QuicheQuicClientCodec(QuicheConfig config, Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                          int localConnIdLength) {
        // Let's just use Quic.MAX_DATAGRAM_SIZE as the maximum size for a token on the client side. This should be
        // safe enough and as we not have too many codecs at the same time this should be ok.
        super(config, localConnIdLength, Quic.MAX_DATAGRAM_SIZE);
        this.sslEngineProvider = sslEngineProvider;
    }

    @Override
    protected QuicheQuicChannel quicPacketRead(
            ChannelHandlerContext ctx, InetSocketAddress sender, InetSocketAddress recipient,
            QuicPacketType type, int version, ByteBuf scid, ByteBuf dcid,
            ByteBuf token) {
        ByteBuffer key = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        return getChannel(key);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) {
        final QuicheQuicChannel channel;
        try {
            channel = QuicheQuicChannel.handleConnect(sslEngineProvider, remoteAddress, config.nativeAddress(),
                    localConnIdLength, config.isDatagramSupported());
        } catch (Exception e) {
            promise.setFailure(e);
            return;
        }
        if (channel != null) {
            putChannel(channel);
            channel.finishConnect();
            promise.setSuccess();
            return;
        }
        ctx.connect(remoteAddress, localAddress, promise);
    }
}
