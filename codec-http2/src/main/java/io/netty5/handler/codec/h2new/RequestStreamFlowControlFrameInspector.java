/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.handler.codec.h2new;

import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.ChannelInputShutdownReadComplete;
import io.netty5.channel.socket.ChannelOutputShutdownEvent;
import io.netty5.handler.codec.h2new.ChannelFlowControlledBytesDistributor.DistributionAcceptor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * A {@link ChannelHandlerAdapter} that feeds flow control events to the associated
 * {@link DefaultChannelFlowControlledBytesDistributor}. <p>
 * This handler <b>must</b> be added to an {@link Http2StreamChannel}.
 */
final class RequestStreamFlowControlFrameInspector extends ChannelHandlerAdapter {
    private final int streamId;
    private final DefaultChannelFlowControlledBytesDistributor distributor;
    private final Http2RequestStreamCodecState localState;
    private final Http2RequestStreamCodecState remoteState;
    private final Deque<PendingWrite> pendingWriteQueue;
    private ChannelHandlerContext ctx;
    private int unflushedDataFrameBytes;
    private int writableBytes;
    private int readableBytes;
    private boolean draining;

    RequestStreamFlowControlFrameInspector(int streamId, boolean isServer,
                                           DefaultChannelFlowControlledBytesDistributor distributor,
                                           Http2RequestStreamCodecState localState,
                                           Http2RequestStreamCodecState remoteState) {
        this.streamId = streamId;
        this.distributor = checkNotNullWithIAE(distributor, "distributor");
        this.localState = checkNotNullWithIAE(localState, "localState");
        this.remoteState = checkNotNullWithIAE(remoteState, "remoteState");
        pendingWriteQueue = new ArrayDeque<>(4);
        if (isLocallyInitiatedStream(streamId, isServer)) {
            distributor.newLocallyInitiatedStream(streamId);
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        distributor.replaceRemote(streamId, new DistributionAcceptor() {
            @Override
            public void accumulate(int accumulate) {
                writableBytes += accumulate;
                tryDrainPending();
            }

            @Override
            public int dispose() {
                final int leftover = writableBytes;
                writableBytes = 0;
                return leftover;
            }
        });
        distributor.replaceLocal(streamId, new DistributionAcceptor() {
            @Override
            public void accumulate(int accumulate) {
                readableBytes += accumulate;
                if (accumulate > 0) {
                    ctx.channel().writeAndFlush(new DefaultHttp2WindowUpdateFrame(streamId, accumulate));
                }
            }

            @Override
            public int dispose() {
                final int leftover = readableBytes;
                readableBytes = 0;
                return leftover;
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        distributor.streamOutputClosed(streamId);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final boolean wasTerminated = remoteState.terminated();
        ctx.fireChannelRead(msg);
        if (!wasTerminated && msg instanceof Http2DataFrame) {
            distributor.bytesRead(streamId, ((Http2DataFrame) msg).initialFlowControlledBytes());
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        // Data frames are flow controlled and we have to maintain order between headers and data, so we buffer both
        // the frames.
        if (msg instanceof Http2HeadersFrame || msg instanceof Http2DataFrame) {
            // Still receiving headers or data, buffer till we can write.
            if (msg instanceof Http2DataFrame) {
                final Http2DataFrame dataFrame = (Http2DataFrame) msg;
                unflushedDataFrameBytes += dataFrame.initialFlowControlledBytes();
                // TODO: Coalesce data frames if possible.
            }
            final Promise<Void> promise = ctx.executor().newPromise();
            pendingWriteQueue.addLast(new PendingWrite((Http2Frame) msg, promise));
            return promise.asFuture();
        } else {
            return ctx.write(msg);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (!pendingWriteQueue.isEmpty()) {
            pendingWriteQueue.addLast(FlushPending.FLUSH_PENDING);
            if (!tryDrainPending()) {
                ctx.flush(); // We may have written non-flow controlled frames that need to be flushed.
            }
        } else {
            ctx.flush();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelOutputShutdownEvent) {
            distributor.streamOutputClosed(streamId);
        }
        if (evt instanceof ChannelInputShutdownReadComplete) {
            distributor.streamInputClosed(streamId);
        }
        ctx.fireUserEventTriggered(evt);
    }

    private boolean tryDrainPending() {
        assert ctx != null;
        if (draining) {
            return false;
        }
        draining = true;
        // TODO: Write partially when coalescing data frames.
        boolean flushSeen = false;
        try {
            while (writableBytes >= unflushedDataFrameBytes) {
                final PendingWrite entry = pendingWriteQueue.peekFirst();
                if (entry == null) {
                    break;
                }
                pendingWriteQueue.remove();
                if (entry == FlushPending.FLUSH_PENDING) {
                    flushSeen = true;
                } else {
                    if (entry.frame instanceof Http2DataFrame) {
                        final int bytes = ((Http2DataFrame) entry.frame).initialFlowControlledBytes();
                        unflushedDataFrameBytes -= bytes;
                        distributor.bytesWritten(streamId, bytes);
                    }
                    ctx.write(entry.frame).cascadeTo(entry.promise);
                }
            }
        } finally {
            draining = false;
            if (flushSeen) {
                ctx.flush();
            }
        }
        return flushSeen;
    }

    private static boolean isLocallyInitiatedStream(int streamId, boolean isServer) {
        boolean isEven = streamId % 2 == 0;
        return (isServer && isEven) || (!isServer && !isEven);
    }

    private static class PendingWrite {
        private final Http2Frame frame;
        private final Promise<Void> promise;

        PendingWrite(Http2Frame frame, Promise<Void> promise) {
            this.frame = frame;
            this.promise = promise;
        }
    }

    private static final class FlushPending extends RequestStreamFlowControlFrameInspector.PendingWrite {
        private static final FlushPending FLUSH_PENDING = new FlushPending();

        private FlushPending() {
            super(null, null);
        }
    }
}
