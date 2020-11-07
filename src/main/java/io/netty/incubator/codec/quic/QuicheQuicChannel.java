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
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

final class QuicheQuicChannel extends AbstractChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private final ChannelHandlerContext ctx;
    private final long[] readableStreams = new long[1024];
    private final long[] writableStreams = new long[1024];
    private final LongObjectMap<QuicheQuicStreamChannel> streams = new LongObjectHashMap<>();
    private final InetSocketAddress remote;
    private final ChannelConfig config;
    private long connAddr;

    private volatile boolean active = true;

    QuicheQuicChannel(long connAddr, ChannelHandlerContext ctx, InetSocketAddress remote, ChannelHandler handler) {
        super(ctx.channel());
        config = new DefaultChannelConfig(this);
        this.connAddr = connAddr;
        this.ctx = ctx;
        this.remote = remote;
        pipeline().addLast(handler);
    }

    @Override
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this) {
            @Override
            protected void onUnhandledInboundMessage(ChannelHandlerContext ctx, Object msg) {
                QuicheQuicStreamChannel channel = (QuicheQuicStreamChannel) msg;
                eventLoop().register(channel);
            }
        };
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new QuicChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop eventLoop) {
        return parent().eventLoop() == eventLoop;
    }

    @Override
    protected SocketAddress localAddress0() {
        return parent().localAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return remote;
    }

    @Override
    protected void doBind(SocketAddress socketAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        active = false;
        Quiche.throwIfError(Quiche.quiche_conn_close(connAddr, false, 0,
                Unpooled.EMPTY_BUFFER.memoryAddress(), 0));
    }

    @Override
    protected void doBeginRead() throws Exception {
        // TODO: handle this
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return active;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    long connAddr() {
        return connAddr;
    }

    boolean freeIfClosed() {
        if (Quiche.quiche_conn_is_closed(connAddr)) {
            Quiche.quiche_conn_free(connAddr);
            connAddr = -1;
            return true;
        }
        return false;
    }

    private boolean fireChannelReadCompletePending;

    /**
     * Receive some data on a quic connection. The given {@link ByteBuf} may be modified in place so if you depend on
     * the fact that the contents are not modified you will need to make a copy before.
     */
    void recv(ByteBuf buffer) {
        fireChannelReadCompletePending |= ((QuicChannelUnsafe) unsafe()).recvForConnection(buffer);
    }

    void fireChannelReadCompleteIfNeeded() {
        if (fireChannelReadCompletePending) {
            fireChannelReadCompletePending = false;
            pipeline().fireChannelReadComplete();
        }
    }

    void flushStreams() {
        if (Quiche.quiche_conn_is_established(connAddr) || Quiche.quiche_conn_is_in_early_data(connAddr)) {
            int writable = Quiche.quiche_conn_writable(connAddr, writableStreams);
            System.err.println("writable streams " + writable);

            for (int i = 0; i < writable; i++) {
                long stream = writableStreams[writable];
                streams.get(stream).forceFlush();
            }
        }
    }

    boolean flushEgress() {
        boolean writeDone = false;
        for (;;) {
            ByteBuf out = ctx.alloc().directBuffer(Quic.MAX_DATAGRAM_SIZE);
            int writerIndex = out.writerIndex();
            int written = Quiche.quiche_conn_send(connAddr, out.memoryAddress() + writerIndex, out.writableBytes());

            try {
                if (Quiche.throwIfError(written)) {
                    out.release();
                    return writeDone;
                }
            } catch (Exception e) {
                out.release();
                ctx.fireExceptionCaught(e);
                return writeDone;
            }

            out.writerIndex(writerIndex + written);
            ctx.write(new DatagramPacket(out, remote));
            writeDone = true;
        }
    }

    private final class QuicChannelUnsafe extends AbstractChannel.AbstractUnsafe {
        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress1, ChannelPromise channelPromise) {
            // TODO: This needs some real implementation so its possible to create streams from an existing connection.
            //       This is true for client and server side as streams can be create on both in QUIC land.
            channelPromise.setFailure(new UnsupportedOperationException());
        }

        boolean recvForConnection(ByteBuf buffer) {
            int bufferReadable = buffer.readableBytes();
            int bufferReaderIndex = buffer.readerIndex();
            long memoryAddress = buffer.memoryAddress() + bufferReaderIndex;
            boolean fireChannelRead = false;

            try {
                do  {
                    // Call quiche_conn_recv(...) until we consumed all bytes or we did receive some error.
                    int res = Quiche.quiche_conn_recv(connAddr, memoryAddress, bufferReadable);
                    boolean done;
                    try {
                        done = Quiche.throwIfError(res);
                    } catch (Exception e) {
                        pipeline().fireExceptionCaught(e);
                        done = true;
                    }

                    fireChannelRead |= handleReadableStreams();
                    if (done) {
                        break;
                    } else {
                        memoryAddress += res;
                        bufferReadable -= res;
                    }
                } while (bufferReadable > 0);
                return fireChannelRead;
            } finally {
                buffer.skipBytes((int) (memoryAddress - buffer.memoryAddress()));
            }
        }

        // TODO: Improve the way how we handle channelReadComplete() for the streams to batch as many
        //       fireChannelRead(...) as possible per stream
        private boolean handleReadableStreams() {
            boolean fireChannelRead = false;
            if (Quiche.quiche_conn_is_established(connAddr) || Quiche.quiche_conn_is_in_early_data(connAddr)) {
                int readable = Quiche.quiche_conn_readable(connAddr, readableStreams);
                for (int i = 0; i < readable; i++) {
                    long streamId = readableStreams[i];
                    QuicheQuicStreamChannel streamChannel = streams.get(streamId);
                    if (streamChannel == null) {
                        streamChannel = new QuicheQuicStreamChannel(QuicheQuicChannel.this, streamId);
                        streams.put(streamId, streamChannel);
                        fireChannelRead = true;
                        pipeline().fireChannelRead(streamChannel);
                    } else {
                        streamChannel.recvIfPending();
                    }
                }
            }
            return fireChannelRead;
        }
    }
}
