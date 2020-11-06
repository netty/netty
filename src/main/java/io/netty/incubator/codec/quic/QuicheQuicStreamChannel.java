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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.DuplexChannel;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.SocketAddress;

final class QuicheQuicStreamChannel extends AbstractChannel implements DuplexChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);
    private static final int FIN_LEN = 1;

    private final ChannelConfig config;
    private final long streamId;
    private boolean readPending;
    private boolean flushPending;

    private volatile boolean active = true;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    QuicheQuicStreamChannel(QuicheQuicChannel parent, long streamId) {
        super(parent);
        config = new DefaultChannelConfig(this);
        this.streamId = streamId;
    }

    private static void handleRes(int res, ChannelPromise promise) {
        if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
            promise.setFailure(Quiche.newException(res));
        } else {
            promise.setSuccess();
        }
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown;
    }

    @Override
    public ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    public ChannelFuture shutdownInput(ChannelPromise channelPromise) {
        if (eventLoop().inEventLoop()) {
            shutdownInput0(channelPromise);
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    shutdownInput0(channelPromise);
                }
            });
        }
        return channelPromise;
    }

    public void shutdownInput0(ChannelPromise channelPromise) {
        inputShutdown = true;
        handleRes(Quiche.quiche_conn_stream_shutdown(
                connectionAddr(), streamId, Quiche.QUICHE_SHUTDOWN_READ, 0), channelPromise);
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown;
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(ChannelPromise channelPromise) {
        if (eventLoop().inEventLoop()) {
            shutdownOutput0(channelPromise);
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    shutdownOutput0(channelPromise);
                }
            });
        }
        return channelPromise;
    }

    public void shutdownOutput0(ChannelPromise channelPromise) {
        outputShutdown = true;
        handleRes(Quiche.quiche_conn_stream_shutdown(
                connectionAddr(), streamId, Quiche.QUICHE_SHUTDOWN_WRITE, 0), channelPromise);
    }

    @Override
    public boolean isShutdown() {
        return outputShutdown && inputShutdown;
    }

    @Override
    public ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    @Override
    public ChannelFuture shutdown(ChannelPromise channelPromise) {
        if (eventLoop().inEventLoop()) {
            shutdown0(channelPromise);
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    shutdown0(channelPromise);
                }
            });
        }
        return channelPromise;
    }

    public void shutdown0(ChannelPromise channelPromise) {
        inputShutdown = true;
        outputShutdown = true;

        int resRead = Quiche.quiche_conn_stream_shutdown(
                connectionAddr(), streamId, Quiche.QUICHE_SHUTDOWN_READ, 0);
        int resWrite = Quiche.quiche_conn_stream_shutdown(
                connectionAddr(), streamId, Quiche.QUICHE_SHUTDOWN_WRITE, 0);
        handleRes(resRead | resWrite, channelPromise);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new QuicStreamChannelUnsafe();
    }

    @Override
    protected boolean isCompatible(EventLoop eventLoop) {
        return eventLoop == parent().eventLoop();
    }

    @Override
    protected SocketAddress localAddress0() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doBind(SocketAddress socketAddress) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        active = false;
        // Just write an empty buffer and set fin to true.
        int res = Quiche.quiche_conn_stream_send(connectionAddr(), streamId,
                Unpooled.EMPTY_BUFFER.memoryAddress(), 0, true);
        if (res < 0 && res != Quiche.QUICHE_ERR_DONE) {
            throw Quiche.newException(res);
        }
    }

    @Override
    protected void doBeginRead() {
        readPending = true;
        ((QuicStreamChannelUnsafe) unsafe()).recv();
    }

    private long connectionAddr() {
        return ((QuicheQuicChannel) parent()).connAddr();
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (!(msg instanceof ByteBuf) ||
                // TODO: Copy to direct memory.
                !((ByteBuf) msg).hasMemoryAddress()) {
            throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
        return msg;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        for (;;) {
            ByteBuf buffer = (ByteBuf) channelOutboundBuffer.current();
            if (buffer == null) {
                break;
            }
            int readable = buffer.readableBytes();
            int res = Quiche.quiche_conn_stream_send(connectionAddr(), streamId,
                    buffer.memoryAddress() + buffer.readerIndex(), readable, false);

            if (res == readable) {
                channelOutboundBuffer.remove();
            } else if (res < 0) {
                if (res == Quiche.QUICHE_ERR_DONE) {
                    // stream has no capacity left stop trying to send.
                    flushPending = true;
                    break;
                }
                throw Quiche.newException(res);
            } else {
                // partial write
                channelOutboundBuffer.removeBytes(res);
                break;
            }
        }
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

    void forceFlush() {
        ((QuicStreamChannelUnsafe) unsafe()).forceFlush();
    }

    void recvIfPending() {
        if (readPending) {
            ((QuicStreamChannelUnsafe) unsafe()).recv();
        } else {
            readPending = true;
        }
    }

    private final class QuicStreamChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress1, ChannelPromise channelPromise) {
            channelPromise.setFailure(new UnsupportedOperationException("Not supported by streams"));
        }

        @Override
        protected void flush0() {
            if (flushPending) {
                return;
            }
            super.flush0();
        }

        void forceFlush() {
            flushPending = false;
            super.flush0();
        }

        private void closeOnRead(ChannelPipeline pipeline) {
            // TODO: Improve
            this.close(this.voidPromise());
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close,
                                         RecvByteBufAllocator.Handle allocHandle) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }

            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);
            if (close || cause instanceof OutOfMemoryError || cause instanceof IOException) {
                this.closeOnRead(pipeline);
            }
        }

        void recv() {
            ChannelConfig config = config();
            ChannelPipeline pipeline = pipeline();
            ByteBufAllocator allocator = config.getAllocator();
            RecvByteBufAllocator.Handle allocHandle = this.recvBufAllocHandle();
            allocHandle.reset(config);
            ByteBuf byteBuf = null;
            boolean close = false;

            try {
                do {
                    byteBuf = allocHandle.allocate(allocator);
                    int writerIndex = byteBuf.writerIndex();
                    long memoryAddress = byteBuf.memoryAddress();
                    int recvLen = Quiche.quiche_conn_stream_recv(connectionAddr(), streamId,
                            memoryAddress + FIN_LEN + writerIndex, byteBuf.writableBytes() - FIN_LEN, memoryAddress);

                    // We
                    close = byteBuf.getBoolean(writerIndex);

                    allocHandle.lastBytesRead(recvLen);
                    if (recvLen == 0 || recvLen == Quiche.QUICHE_ERR_DONE) {
                        byteBuf.release();
                        byteBuf = null;
                        break;
                    }
                    readPending = false;

                    if (recvLen < 0) {
                        throw Quiche.newException(recvLen);
                    }

                    allocHandle.incMessagesRead(1);
                    // Skip the FIN
                    byteBuf.setIndex(FIN_LEN, writerIndex + recvLen + FIN_LEN);
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;
                } while (allocHandle.continueReading() && !close);

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();
                if (close) {
                    closeOnRead(pipeline);
                }
            } catch (Throwable var11) {
                this.handleReadException(pipeline, byteBuf, var11, close, allocHandle);
            }
        }
    }
}
