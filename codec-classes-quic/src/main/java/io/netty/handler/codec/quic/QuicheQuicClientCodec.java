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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link QuicheQuicCodec} for QUIC clients.
 */
final class QuicheQuicClientCodec extends QuicheQuicCodec {

    private final Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider;
    private final Executor sslTaskExecutor;

    QuicheQuicClientCodec(QuicheConfig config, Function<QuicChannel, ? extends QuicSslEngine> sslEngineProvider,
                          Executor sslTaskExecutor, int localConnIdLength, FlushStrategy flushStrategy) {
        // Let's just use Quic.MAX_DATAGRAM_SIZE as the maximum size for a token on the client side. This should be
        // safe enough and as we not have too many codecs at the same time this should be ok.
        super(config, localConnIdLength, flushStrategy);
        this.sslEngineProvider = sslEngineProvider;
        this.sslTaskExecutor = sslTaskExecutor;
    }

    @Override
    @Nullable
    protected QuicheQuicChannel quicPacketRead(
            ChannelHandlerContext ctx, InetSocketAddress sender, InetSocketAddress recipient,
            QuicPacketType type, long version, ByteBuf scid, ByteBuf dcid,
            ByteBuf token, ByteBuf senderSockaddrMemory, ByteBuf recipientSockaddrMemory,
            Consumer<QuicheQuicChannel> freeTask, int localConnIdLength, QuicheConfig config) {
        ByteBuffer key = dcid.internalNioBuffer(dcid.readerIndex(), dcid.readableBytes());
        return getChannel(key);
    }

    @Override
    protected void connectQuicChannel(QuicheQuicChannel channel, SocketAddress remoteAddress,
                                      SocketAddress localAddress, ByteBuf senderSockaddrMemory,
                                      ByteBuf recipientSockaddrMemory, Consumer<QuicheQuicChannel> freeTask,
                                      int localConnIdLength, QuicheConfig config, ChannelPromise promise) {
        try {
            channel.connectNow(sslEngineProvider, sslTaskExecutor, freeTask, config.nativeAddress(),
                    localConnIdLength, config.isDatagramSupported(),
                    senderSockaddrMemory.internalNioBuffer(0, senderSockaddrMemory.capacity()),
                    recipientSockaddrMemory.internalNioBuffer(0, recipientSockaddrMemory.capacity()));
        } catch (Throwable cause) {
            // Only fail the original promise. Cleanup will be done as part of the listener attached to it.
            promise.setFailure(cause);
            return;
        }

        addChannel(channel);
        channel.finishConnect();
        promise.setSuccess();
    }
}
