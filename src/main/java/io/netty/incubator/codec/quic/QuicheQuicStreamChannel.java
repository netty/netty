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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;
import java.util.function.Supplier;

/**
 * {@link QuicStreamChannel} implementation that uses <a href="https://github.com/cloudflare/quiche">quiche</a>.
 */
final class QuicheQuicStreamChannel extends AbstractChannel implements QuicStreamChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private final QuicStreamChannelConfig config;
    private final QuicStreamAddress address;
    private boolean readable;
    private boolean readPending;
    private boolean flushPending;
    private boolean inRecv;
    private boolean finReceived;
    private boolean finSent;

    private volatile boolean active = true;
    private volatile boolean inputShutdown;
    private volatile boolean outputShutdown;

    QuicheQuicStreamChannel(QuicheQuicChannel parent, long streamId) {
        super(parent);
        config = new DefaultQuicStreamChannelConfig(this);
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
            eventLoop().execute(() -> shutdownInput0(channelPromise));
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
            eventLoop().execute(() -> shutdownOutput0(channelPromise));
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
            eventLoop().execute(() -> shutdown0(channelPromise));
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
        if (!finSent) {
            finSent = true;
            parent().streamClose(streamId());
        }
        if (type() == QuicStreamType.UNIDIRECTIONAL && isLocalCreated()) {
            // If its an unidirectional stream and was created locally it is safe to close the stream now as we will
            // never receive data from the other side.
            parent().streamClosed(streamId());
        } else {
            removeStreamFromParent();
        }
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
        if (!(msg instanceof ByteBuf || msg instanceof QuicStreamFrame)) {
            throw new UnsupportedOperationException("unsupported message type: " + StringUtil.simpleClassName(msg));
        }
        return msg;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer channelOutboundBuffer) throws Exception {
        // reset first as streamSendMultiple may notify futures.
        flushPending = false;
        if (finSent) {
            failOutboundBuffer(channelOutboundBuffer, () -> new ChannelOutputShutdownException(
                    "Fin was sent already"));
            return;
        }
        if (type() == QuicStreamType.UNIDIRECTIONAL && !isLocalCreated()) {
            failOutboundBuffer(channelOutboundBuffer, () -> new UnsupportedOperationException(
                    "Writes on non-local created streams that are unidirectional are not supported"));
            return;
        }

        QuicheQuicChannel.StreamSendResult result = parent()
                .streamSendMultiple(streamId(), alloc(), channelOutboundBuffer);
        switch (result) {
            case NO_SPACE:
                parent().streamHasPendingWrites(streamId());
                flushPending = true;
                break;
            case DONE:
                // Did sent everything without any FIN.
                break;
            case FIN:
                failOutboundBuffer(channelOutboundBuffer, () -> new ChannelOutputShutdownException(
                        "Fin was sent already"));
                finSent = true;
                break;
            default:
                throw new Error();
        }
    }

    private void failOutboundBuffer(ChannelOutboundBuffer outboundBuffer, Supplier<Exception> exceptionSupplier) {
        if (outboundBuffer.isEmpty()) {
            // nothing to do.
            return;
        }

        Exception exception = exceptionSupplier.get();
        for (;;) {
            if (!outboundBuffer.remove(exception)) {
                // failed all writes.
                return;
            }
        }
    }

    private void removeStreamFromParent() {
        if (!active && finReceived) {
            parent().streamClosed(streamId());
        }
    }

    @Override
    public QuicStreamChannelConfig config() {
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

    /**
     * Stream is writable.
     */
    void writable() {
        ((QuicStreamChannelUnsafe) unsafe()).flushIfPending();
    }

    /**
     * Stream is readable.
     */
    void readable() {
        // Mark as readable and if a read is pending execute it.
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
                // We already have a flush pending that needs to be done once the stream becomes writable again.
                return;
            }
            boolean wasFinSent = QuicheQuicStreamChannel.this.finSent;
            try {
                super.flush0();
            } finally {
                // Let's check if we should close the channel now.
                // If it's a unidirectional channel we can close it as there will be no fin that we can read
                // from the remote peer. If its an bidirectional channel we should only close the channel if we
                // also received the fin from the remote peer.
                if (!wasFinSent && QuicheQuicStreamChannel.this.finSent
                        && (type() == QuicStreamType.UNIDIRECTIONAL || finReceived)) {
                    // close the channel now
                    close(voidPromise());
                }
            }
        }

        void flushIfPending() {
            if (flushPending) {
                // We had a flush pending, reset it and try to flush again.
                flushPending = false;
                super.flush();
            }
        }

        private void closeOnRead(ChannelPipeline pipeline, boolean readFrames) {
            if (readFrames && finReceived && finSent) {
                close(voidPromise());
            } else if (config.isAllowHalfClosure() && type() == QuicStreamType.BIDIRECTIONAL && active) {
                // If we receive a fin there will be no more data to read so we need to fire both events
                // to be consistent with other transports.
                pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                pipeline.fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            } else {
                // This was an unidirectional stream which means as soon as we received FIN we need
                // close the connection.
                close(voidPromise());
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause,
                                         @SuppressWarnings("deprecation") RecvByteBufAllocator.Handle allocHandle,
                                         boolean readFrames) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }

            readComplete(allocHandle, pipeline);
            pipeline.fireExceptionCaught(cause);
            if (finReceived) {
                closeOnRead(pipeline, readFrames);
            }
        }

        void recv() {
            if (inRecv) {
                // As the use may call read() we need to guard against re-entrancy here as otherwise it could
                // be possible that we re-enter this method while still processing it.
                return;
            }

            inRecv = true;
            try {
                ChannelPipeline pipeline = pipeline();
                QuicStreamChannelConfig config = config();
                ByteBufAllocator allocator = config.getAllocator();
                @SuppressWarnings("deprecation")
                RecvByteBufAllocator.Handle allocHandle = this.recvBufAllocHandle();
                boolean readFrames = config.isReadFrames();

                // We should loop as long as a read() was requested and there is anything left to read, which means the
                // stream was marked as readable before.
                while (active && readPending && readable) {
                    allocHandle.reset(config);
                    ByteBuf byteBuf = null;
                    QuicheQuicChannel parent = parent();
                    // It's possible that the stream was marked as finish while we iterated over the readable streams
                    // or while we did have auto read disabled. If so we need to ensure we not try to read from it as it
                    // would produce an error.
                    boolean readCompleteNeeded = false;
                    boolean continueReading = true;
                    try {
                        while (!finReceived && continueReading) {
                            byteBuf = allocHandle.allocate(allocator);
                            switch (parent.streamRecv(streamId(), byteBuf)) {
                                case DONE:
                                    // Nothing left to read;
                                    readable = false;
                                    break;
                                case FIN:
                                    // If we received a FIN we also should mark the channel as non readable as
                                    // there is nothing left to read really.
                                    readable = false;
                                    finReceived = true;
                                    break;
                                case OK:
                                    break;
                                default:
                                    throw new Error();
                            }
                            allocHandle.lastBytesRead(byteBuf.readableBytes());
                            if (allocHandle.lastBytesRead() <= 0) {
                                byteBuf.release();
                                if (finReceived && readFrames) {
                                    // If we read QuicStreamFrames we should fire an frame through the pipeline
                                    // with an empty buffer but the fin flag set to true.
                                    byteBuf = Unpooled.EMPTY_BUFFER;
                                } else {
                                    byteBuf = null;
                                    break;
                                }
                            }
                            // We did read one message.
                            allocHandle.incMessagesRead(1);
                            readCompleteNeeded = true;

                            // It's important that we reset this to false before we call fireChannelRead(...)
                            // as the user may request another read() from channelRead(...) callback.
                            readPending = false;

                            if (readFrames) {
                                pipeline.fireChannelRead(new DefaultQuicStreamFrame(byteBuf, finReceived));
                            } else {
                                pipeline.fireChannelRead(byteBuf);
                            }
                            byteBuf = null;
                            continueReading = allocHandle.continueReading();
                        }

                        if (readCompleteNeeded) {
                            readComplete(allocHandle, pipeline);
                        }
                        if (finReceived) {
                            readable = false;
                            closeOnRead(pipeline, readFrames);
                        }
                    } catch (Throwable cause) {
                        readable = false;
                        handleReadException(pipeline, byteBuf, cause, allocHandle, readFrames);
                    }
                }
            } finally {
                // About to leave the method lets reset so we can enter it again.
                inRecv = false;
                removeStreamFromParent();
            }
        }

        // Read was complete and something was read, so we we need to reset the readPending flags, the allocHandle
        // and call fireChannelReadComplete(). The user may schedule another read now.
        private void readComplete(@SuppressWarnings("deprecation") RecvByteBufAllocator.Handle allocHandle,
                                  ChannelPipeline pipeline) {
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
        }
    }
}
