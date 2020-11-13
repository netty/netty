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
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.SocketAddress;

final class QuicheQuicStreamChannel extends AbstractChannel implements QuicStreamChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final ChannelConfig config;
    private final QuicStreamAddress address;
    private boolean readable;
    private boolean readPending;
    private boolean flushPending;

    private volatile boolean active = true;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    QuicheQuicStreamChannel(QuicheQuicChannel parent, long streamId) {
        super(parent);
        config = new DefaultChannelConfig(this);
        this.address = new QuicStreamAddress(streamId);
    }

    @Override
    public QuicStreamAddress localAddress() {
        return (QuicStreamAddress) super.localAddress();
    }

    @Override
    public QuicStreamAddress remoteAddress() {
        return (QuicStreamAddress) super.remoteAddress();
    }

    @Override
    public boolean isLocalCreated() {
        return parent().isStreamLocalCreated(streamId());
    }

    @Override
    public QuicStreamType type() {
        return parent().streamType(streamId());
    }

    @Override
    public long streamId() {
        return address.streamId();
    }

    @Override
    public boolean isInputShutdown() {
        return inputShutdown || !isActive();
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

    @Override
    public QuicheQuicChannel parent() {
        return (QuicheQuicChannel) super.parent();
    }

    private void shutdownInput0(ChannelPromise channelPromise) {
        inputShutdown = true;
        parent().streamShutdownRead(streamId(), channelPromise);
    }

    @Override
    public boolean isOutputShutdown() {
        return outputShutdown || !isActive();
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
        parent().streamShutdownWrite(streamId(), channelPromise);
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
        parent().streamShutdownReadAndWrite(streamId(), channelPromise);
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
        return address;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return address;
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
        parent().streamClose(streamId());
    }

    @Override
    protected void doBeginRead() {
        readPending = true;
        if (readable) {
            ((QuicStreamChannelUnsafe) unsafe()).recv();
        }
    }

    @Override
    protected Object filterOutboundMessage(Object msg) {
        if (!(msg instanceof ByteBuf)) {
            throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
        return msg;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        // reset first as streamSendMultiple may notify futures.
        flushPending = false;
        if (!parent().streamSendMultiple(streamId(), alloc(), channelOutboundBuffer)) {
            flushPending = true;
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

    void readable() {
        readable = true;
        if (readPending) {
            ((QuicStreamChannelUnsafe) unsafe()).recv();
        }
    }

    private final class QuicStreamChannelUnsafe extends AbstractUnsafe {
        @Override
        public void connect(SocketAddress socketAddress, SocketAddress socketAddress1, ChannelPromise channelPromise) {
            channelPromise.setFailure(new UnsupportedOperationException());
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
            ChannelPipeline pipeline = pipeline();
            ChannelConfig config = config();
            ByteBufAllocator allocator = config.getAllocator();
            RecvByteBufAllocator.Handle allocHandle = this.recvBufAllocHandle();
            allocHandle.reset(config);
            ByteBuf byteBuf = null;
            boolean close = false;
            boolean readCompleteNeeded = false;
            QuicheQuicChannel parent = parent();
            try {
                do {
                    byteBuf = allocHandle.allocate(allocator);

                    int res = parent.streamRecv(streamId(), byteBuf);
                    if (res == -1) {
                        // Nothing left to read;
                        readable = false;
                    } else if (res == 1) {
                        close = true;
                    }
                    allocHandle.lastBytesRead(byteBuf.readableBytes());
                    if (allocHandle.lastBytesRead() <= 0) {
                        byteBuf.release();
                        byteBuf = null;
                        break;
                    }
                    readPending = false;
                    readCompleteNeeded = true;
                    pipeline.fireChannelRead(byteBuf);
                    byteBuf = null;
                } while (allocHandle.continueReading() && !close);

                allocHandle.readComplete();
                if (readCompleteNeeded) {
                    pipeline.fireChannelReadComplete();
                }
                if (close) {
                    closeOnRead(pipeline);
                }
            } catch (Throwable cause) {
                readPending = false;
                this.handleReadException(pipeline, byteBuf, cause, close, allocHandle);
            }
        }
    }
}
