/*
 * Copyright 2019 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelOutboundInvoker;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoop;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.ServerChannel;
import io.netty5.handler.codec.http2.Http2FrameCodec.DefaultHttp2FrameStream;
import io.netty5.util.Resource;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureContextListener;
import io.netty5.util.internal.UnstableApi;

import javax.net.ssl.SSLException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

import static io.netty5.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty5.handler.codec.http2.Http2Exception.connectionError;

/**
 * An HTTP/2 handler that creates child channels for each stream. This handler must be used in combination
 * with {@link Http2FrameCodec}.
 *
 * <p>When a new stream is created, a new {@link Channel} is created for it. Applications send and
 * receive {@link Http2StreamFrame}s on the created channel. {@link Buffer}s cannot be processed by the channel;
 * all writes that reach the head of the pipeline must be an instance of {@link Http2StreamFrame}. Writes that reach
 * the head of the pipeline are processed directly by this handler and cannot be intercepted.
 *
 * <p>The child channel will be notified of user events that impact the stream, such as {@link
 * Http2GoAwayFrame} and {@link Http2ResetFrame}, as soon as they occur. Although {@code
 * Http2GoAwayFrame} and {@code Http2ResetFrame} signify that the remote is ignoring further
 * communication, closing of the channel is delayed until any inbound queue is drained with {@link
 * ChannelOutboundInvoker#read(ReadBufferAllocator)}, which follows the default behavior of channels in Netty.
 * Applications are free to close the channel in response to such events if they don't have use for any queued
 * messages. Any connection level events like {@link Http2SettingsFrame} and {@link Http2GoAwayFrame}
 * will be processed internally and also propagated down the pipeline for other handlers to act on.
 *
 * <p>Outbound streams are supported via the {@link Http2StreamChannelBootstrap}.
 *
 * <p>{@link ChannelOption#AUTO_READ} is supported.
 *
 * <h3>Resources</h3>
 *
 * Some {@link Http2StreamFrame}s implement the {@link Resource} interface, as they carry
 * resource objects (e.g. {@link Buffer}s). An application handler needs to close or dispose of
 * such objects after having consumed them.

 * <h3>Channel Events</h3>
 *
 * A child channel becomes active as soon as it is registered to an {@link EventLoop}. Therefore, an active channel
 * does not map to an active HTTP/2 stream immediately. Only once a {@link Http2HeadersFrame} has been successfully sent
 * or received, does the channel map to an active HTTP/2 stream. In case it is not possible to open a new HTTP/2 stream
 * (i.e. due to the maximum number of active streams being exceeded), the child channel receives an exception
 * indicating the cause and is closed immediately thereafter.
 *
 * <h3>Writability and Flow Control</h3>
 *
 * A child channel observes outbound/remote flow control via the channel's writability. A channel only becomes writable
 * when it maps to an active HTTP/2 stream . A child channel does not know about the connection-level flow control
 * window. {@link ChannelHandler}s are free to ignore the channel's writability, in which case the excessive writes will
 * be buffered by the parent channel. It's important to note that only {@link Http2DataFrame}s are subject to
 * HTTP/2 flow control.
 *
 * <h3>Closing a {@link Http2StreamChannel}</h3>
 *
 * Once you close a {@link Http2StreamChannel} a {@link Http2ResetFrame} will be sent to the remote peer with
 * {@link Http2Error#CANCEL} if needed. If you want to close the stream with another {@link Http2Error} (due
 * errors / limits) you should propagate a {@link Http2FrameStreamException} through the {@link ChannelPipeline}.
 * Once it reaches the end of the {@link ChannelPipeline} it will automatically close the {@link Http2StreamChannel}
 * and send a {@link Http2ResetFrame} with the unwrapped {@link Http2Error} set. Another possibility is to just
 * directly write a {@link Http2ResetFrame} to the {@link Http2StreamChannel}l.
 */
@UnstableApi
public final class Http2MultiplexHandler extends Http2ChannelDuplexHandler {

    private static final FutureContextListener<Channel, Void> CHILD_CHANNEL_REGISTRATION_LISTENER =
            Http2MultiplexHandler::registerDone;

    private final ChannelHandler inboundStreamHandler;
    private final ChannelHandler upgradeStreamHandler;
    private final Queue<DefaultHttp2StreamChannel> readCompletePendingQueue =
            new MaxCapacityQueue<>(new ArrayDeque<>(8),
                    // Choose 100 which is what is used most of the times as default.
                    Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS);

    private boolean parentReadInProgress;
    private int idCount;

    // Need to be volatile as accessed from within the Http2MultiplexHandlerStreamChannel in a multi-threaded fashion.
    private volatile ChannelHandlerContext ctx;

    /**
     * Creates a new instance
     *
     * @param inboundStreamHandler the {@link ChannelHandler} that will be added to the {@link ChannelPipeline} of
     *                             the {@link Channel}s created for new inbound streams.
     */
    public Http2MultiplexHandler(ChannelHandler inboundStreamHandler) {
        this(inboundStreamHandler, null);
    }

    /**
     * Creates a new instance
     *
     * @param inboundStreamHandler the {@link ChannelHandler} that will be added to the {@link ChannelPipeline} of
     *                             the {@link Channel}s created for new inbound streams.
     * @param upgradeStreamHandler the {@link ChannelHandler} that will be added to the {@link ChannelPipeline} of the
     *                             upgraded {@link Channel}.
     */
    public Http2MultiplexHandler(ChannelHandler inboundStreamHandler, ChannelHandler upgradeStreamHandler) {
        this.inboundStreamHandler = Objects.requireNonNull(inboundStreamHandler, "inboundStreamHandler");
        this.upgradeStreamHandler = upgradeStreamHandler;
    }

    private static void registerDone(Channel childChannel, Future<?> future) {
        // Handle any errors that occurred on the local thread while registering. Even though
        // failures can happen after this point, they will be handled by the channel by closing the
        // childChannel.
        if (future.isFailed()) {
            if (childChannel.isRegistered()) {
                childChannel.close();
            } else {
                ((DefaultHttp2StreamChannel) childChannel).closeForcibly();
            }
        }
    }

    @Override
    protected void handlerAdded0(ChannelHandlerContext ctx) {
        if (ctx.executor().inEventLoop() != ctx.channel().executor().inEventLoop()) {
            throw new IllegalStateException("EventExecutor must be on the same thread as the EventLoop of the Channel");
        }
        this.ctx = ctx;
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        readCompletePendingQueue.clear();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        parentReadInProgress = true;
        if (msg instanceof Http2StreamFrame) {
            if (msg instanceof Http2WindowUpdateFrame) {
                // We dont want to propagate update frames to the user
                return;
            }
            Http2StreamFrame streamFrame = (Http2StreamFrame) msg;
            DefaultHttp2FrameStream s =
                    (DefaultHttp2FrameStream) streamFrame.stream();

            DefaultHttp2StreamChannel channel = (DefaultHttp2StreamChannel) s.attachment;
            if (streamFrame instanceof Http2ResetFrame || streamFrame instanceof Http2PriorityFrame) {
                // Reset and Priority frames needs to be propagated via user events as these are not flow-controlled and
                // so must not be controlled by suppressing channel.read() on the child channel.
                channel.pipeline().fireChannelInboundEvent(streamFrame);

                // RST frames will also trigger closing of the streams which then will call
                // AbstractHttp2StreamChannel.streamClosed()
            } else {
                channel.fireChildRead(streamFrame);
            }
            return;
        }

        if (msg instanceof Http2GoAwayFrame) {
            // goaway frames will also trigger closing of the streams which then will call
            // AbstractHttp2StreamChannel.streamClosed()
            onHttp2GoAwayFrame(ctx, (Http2GoAwayFrame) msg);
        }

        // Send everything down the pipeline
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            // While the writability state may change during iterating of the streams we just set all of the streams
            // to writable to not affect fairness. These will be "limited" by their own watermarks in any case.
            forEachActiveStream(DefaultHttp2StreamChannel.WRITABLE_VISITOR);
        }

        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof Http2FrameStreamEvent) {
            Http2FrameStreamEvent event = (Http2FrameStreamEvent) evt;
            DefaultHttp2FrameStream stream = (DefaultHttp2FrameStream) event.stream();
            if (event.type() == Http2FrameStreamEvent.Type.State) {
                switch (stream.state()) {
                    case HALF_CLOSED_LOCAL:
                        // Ignore everything which was not caused by an upgrade
                        if (stream.id() != Http2CodecUtil.HTTP_UPGRADE_STREAM_ID) {
                            break;
                        }
                        // fall-through
                    case HALF_CLOSED_REMOTE:
                        // fall-through
                    case OPEN:
                        createStreamChannelIfNeeded(stream);
                        break;
                    case CLOSED:
                        DefaultHttp2StreamChannel channel = (DefaultHttp2StreamChannel) stream.attachment;
                        if (channel != null) {
                            channel.streamClosed();
                        }
                        break;
                    default:
                        // ignore for now
                        break;
                }
            }
            return;
        }
        ctx.fireChannelInboundEvent(evt);
    }

    private void createStreamChannelIfNeeded(DefaultHttp2FrameStream stream)
            throws Http2Exception {
        if (stream.attachment != null) {
            // ignore if child channel was already created.
            return;
        }
        final DefaultHttp2StreamChannel ch;
        // We need to handle upgrades special when on the client side.
        if (stream.id() == Http2CodecUtil.HTTP_UPGRADE_STREAM_ID && !isServer(ctx)) {
            // We must have an upgrade handler or else we can't handle the stream
            if (upgradeStreamHandler == null) {
                throw connectionError(INTERNAL_ERROR,
                        "Client is misconfigured for upgrade requests");
            }
            ch = new DefaultHttp2StreamChannel(this, stream, ++idCount, upgradeStreamHandler);
            ch.closeOutbound();
        } else {
            ch = new DefaultHttp2StreamChannel(this, stream, ++idCount, inboundStreamHandler);
        }
        Future<Void> future = ch.register();
        if (future.isDone()) {
            registerDone(ch, future);
        } else {
            future.addListener(ch, CHILD_CHANNEL_REGISTRATION_LISTENER);
        }
    }

    // TODO: This is most likely not the best way to expose this, need to think more about it.
    Http2StreamChannel newOutboundStream() {
        return new DefaultHttp2StreamChannel(this, (DefaultHttp2FrameStream) newStream(), ++idCount, null);
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof Http2FrameStreamException) {
            Http2FrameStreamException exception = (Http2FrameStreamException) cause;
            Http2FrameStream stream = exception.stream();
            DefaultHttp2StreamChannel childChannel = (DefaultHttp2StreamChannel)
                    ((DefaultHttp2FrameStream) stream).attachment;
            try {
                childChannel.pipeline().fireChannelExceptionCaught(cause.getCause());
            } finally {
                // Close with the correct error that causes this stream exception.
                // See https://github.com/netty/netty/issues/13235#issuecomment-1441994672
                childChannel.closeWithError(exception.error());
            }
            return;
        }
        if (cause instanceof Http2MultiplexActiveStreamsException) {
            // Unwrap the cause that was used to create it and fire it for all the active streams.
            fireExceptionCaughtForActiveStream(cause.getCause());
            return;
        }

        if (cause.getCause() instanceof SSLException) {
            fireExceptionCaughtForActiveStream(cause);
        }
        ctx.fireChannelExceptionCaught(cause);
    }

    private void fireExceptionCaughtForActiveStream(final Throwable cause) throws Http2Exception {
        forEachActiveStream(new Http2FrameStreamVisitor() {
            @Override
            public boolean visit(Http2FrameStream stream) {
                DefaultHttp2StreamChannel childChannel = (DefaultHttp2StreamChannel)
                        ((DefaultHttp2FrameStream) stream).attachment;
                childChannel.pipeline().fireChannelExceptionCaught(cause);
                return true;
            }
        });
    }

    private static boolean isServer(ChannelHandlerContext ctx) {
        return ctx.channel().parent() instanceof ServerChannel;
    }

    private void onHttp2GoAwayFrame(ChannelHandlerContext ctx, final Http2GoAwayFrame goAwayFrame) {
        if (goAwayFrame.lastStreamId() == Integer.MAX_VALUE) {
            // None of the streams can have an id greater than Integer.MAX_VALUE
            return;
        }
        // Notify which streams were not processed by the remote peer and are safe to retry on another connection:
        try {
            final boolean server = isServer(ctx);
            forEachActiveStream(stream -> {
                final int streamId = stream.id();
                if (streamId > goAwayFrame.lastStreamId() && Http2CodecUtil.isStreamIdValid(streamId, server)) {
                    final DefaultHttp2StreamChannel childChannel = (DefaultHttp2StreamChannel)
                            ((DefaultHttp2FrameStream) stream).attachment;
                    childChannel.pipeline().fireChannelInboundEvent(goAwayFrame.copy());
                }
                return true;
            });
        } catch (Http2Exception e) {
            ctx.fireChannelExceptionCaught(e);
            ctx.close();
        }
    }

    /**
     * Notifies any child streams of the read completion.
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        processPendingReadCompleteQueue();
        ctx.fireChannelReadComplete();
    }

    private void processPendingReadCompleteQueue() {
        parentReadInProgress = true;
        // If we have many child channel we can optimize for the case when multiple call flush() in
        // channelReadComplete(...) callbacks and only do it once as otherwise we will end-up with multiple
        // write calls on the socket which is expensive.
        DefaultHttp2StreamChannel childChannel = readCompletePendingQueue.poll();
        if (childChannel != null) {
            try {
                do {
                    childChannel.fireChildReadComplete();
                    childChannel = readCompletePendingQueue.poll();
                } while (childChannel != null);
            } finally {
                parentReadInProgress = false;
                readCompletePendingQueue.clear();
                ctx.flush();
            }
        } else {
            parentReadInProgress = false;
        }
    }

    boolean isParentReadInProgress() {
        return parentReadInProgress;
    }

    void addChannelToReadCompletePendingQueue(DefaultHttp2StreamChannel channel) {
        // If there is no space left in the queue, just keep on processing everything that is already
        // stored there and try again.
        while (!readCompletePendingQueue.offer(channel)) {
            processPendingReadCompleteQueue();
        }
    }

    ChannelHandlerContext parentContext() {
        return ctx;
    }
}
