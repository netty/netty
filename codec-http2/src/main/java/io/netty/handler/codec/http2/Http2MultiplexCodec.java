/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCounted;

import io.netty.util.internal.UnstableApi;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_STREAM_ID;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

/**
 * An HTTP/2 handler that creates child channels for each stream.
 *
 * <p>When a new stream is created, a new {@link Channel} is created for it. Applications send and
 * receive {@link Http2StreamFrame}s on the created channel. {@link ByteBuf}s cannot be processed by the channel;
 * all writes that reach the head of the pipeline must be an instance of {@link Http2StreamFrame}. Writes that reach
 * the head of the pipeline are processed directly by this handler and cannot be intercepted.
 *
 * <p>The child channel will be notified of user events that impact the stream, such as {@link
 * Http2GoAwayFrame} and {@link Http2ResetFrame}, as soon as they occur. Although {@code
 * Http2GoAwayFrame} and {@code Http2ResetFrame} signify that the remote is ignoring further
 * communication, closing of the channel is delayed until any inbound queue is drained with {@link
 * Channel#read()}, which follows the default behavior of channels in Netty. Applications are
 * free to close the channel in response to such events if they don't have use for any queued
 * messages. Any connection level events like {@link Http2SettingsFrame} and {@link Http2GoAwayFrame}
 * will be processed internally and also propagated down the pipeline for other handlers to act on.
 *
 * <p>Outbound streams are supported via the {@link Http2StreamChannelBootstrap}.
 *
 * <p>{@link ChannelConfig#setMaxMessagesPerRead(int)} and {@link ChannelConfig#setAutoRead(boolean)} are supported.
 *
 * <h3>Reference Counting</h3>
 *
 * Some {@link Http2StreamFrame}s implement the {@link ReferenceCounted} interface, as they carry
 * reference counted objects (e.g. {@link ByteBuf}s). The multiplex codec will call {@link ReferenceCounted#retain()}
 * before propagating a reference counted object through the pipeline, and thus an application handler needs to release
 * such an object after having consumed it. For more information on reference counting take a look at
 * https://netty.io/wiki/reference-counted-objects.html
 *
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
 * when it maps to an active HTTP/2 stream and the stream's flow control window is greater than zero. A child channel
 * does not know about the connection-level flow control window. {@link ChannelHandler}s are free to ignore the
 * channel's writability, in which case the excessive writes will be buffered by the parent channel. It's important to
 * note that only {@link Http2DataFrame}s are subject to HTTP/2 flow control.
 *
 * @deprecated use {@link Http2FrameCodecBuilder} together with {@link Http2MultiplexHandler}.
 */
@Deprecated
@UnstableApi
public class Http2MultiplexCodec extends Http2FrameCodec {

    private final ChannelHandler inboundStreamHandler;
    private final ChannelHandler upgradeStreamHandler;
    private final Queue<AbstractHttp2StreamChannel> readCompletePendingQueue =
            new MaxCapacityQueue<AbstractHttp2StreamChannel>(new ArrayDeque<AbstractHttp2StreamChannel>(8),
                    // Choose 100 which is what is used most of the times as default.
                    Http2CodecUtil.SMALLEST_MAX_CONCURRENT_STREAMS);

    private boolean parentReadInProgress;
    private int idCount;

    // Need to be volatile as accessed from within the Http2MultiplexCodecStreamChannel in a multi-threaded fashion.
    volatile ChannelHandlerContext ctx;

    Http2MultiplexCodec(Http2ConnectionEncoder encoder,
                        Http2ConnectionDecoder decoder,
                        Http2Settings initialSettings,
                        ChannelHandler inboundStreamHandler,
                        ChannelHandler upgradeStreamHandler, boolean decoupleCloseAndGoAway) {
        super(encoder, decoder, initialSettings, decoupleCloseAndGoAway);
        this.inboundStreamHandler = inboundStreamHandler;
        this.upgradeStreamHandler = upgradeStreamHandler;
    }

    @Override
    public void onHttpClientUpgrade() throws Http2Exception {
        // We must have an upgrade handler or else we can't handle the stream
        if (upgradeStreamHandler == null) {
            throw connectionError(INTERNAL_ERROR, "Client is misconfigured for upgrade requests");
        }
        // Creates the Http2Stream in the Connection.
        super.onHttpClientUpgrade();
    }

    @Override
    public final void handlerAdded0(ChannelHandlerContext ctx) throws Exception {
        if (ctx.executor() != ctx.channel().eventLoop()) {
            throw new IllegalStateException("EventExecutor must be EventLoop of Channel");
        }
        this.ctx = ctx;
    }

    @Override
    public final void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved0(ctx);

        readCompletePendingQueue.clear();
    }

    @Override
    final void onHttp2Frame(ChannelHandlerContext ctx, Http2Frame frame) {
        if (frame instanceof Http2StreamFrame) {
            Http2StreamFrame streamFrame = (Http2StreamFrame) frame;
            AbstractHttp2StreamChannel channel  = (AbstractHttp2StreamChannel)
                    ((DefaultHttp2FrameStream) streamFrame.stream()).attachment;
            channel.fireChildRead(streamFrame);
            return;
        }
        if (frame instanceof Http2GoAwayFrame) {
            onHttp2GoAwayFrame(ctx, (Http2GoAwayFrame) frame);
        }
        // Send frames down the pipeline
        ctx.fireChannelRead(frame);
    }

    @Override
    final void onHttp2StreamStateChanged(ChannelHandlerContext ctx, DefaultHttp2FrameStream stream) {
        switch (stream.state()) {
            case HALF_CLOSED_LOCAL:
                if (stream.id() != HTTP_UPGRADE_STREAM_ID) {
                    // Ignore everything which was not caused by an upgrade
                    break;
                }
                // fall-through
            case HALF_CLOSED_REMOTE:
                // fall-through
            case OPEN:
                if (stream.attachment != null) {
                    // ignore if child channel was already created.
                    break;
                }
                final Http2MultiplexCodecStreamChannel streamChannel;
                // We need to handle upgrades special when on the client side.
                if (stream.id() == HTTP_UPGRADE_STREAM_ID && !connection().isServer()) {
                    // Add our upgrade handler to the channel and then register the channel.
                    // The register call fires the channelActive, etc.
                    assert upgradeStreamHandler != null;
                    streamChannel = new Http2MultiplexCodecStreamChannel(stream, upgradeStreamHandler);
                    streamChannel.closeOutbound();
                } else {
                    streamChannel = new Http2MultiplexCodecStreamChannel(stream, inboundStreamHandler);
                }
                ChannelFuture future = ctx.channel().eventLoop().register(streamChannel);
                if (future.isDone()) {
                    Http2MultiplexHandler.registerDone(future);
                } else {
                    future.addListener(Http2MultiplexHandler.CHILD_CHANNEL_REGISTRATION_LISTENER);
                }
                break;
            case CLOSED:
                AbstractHttp2StreamChannel channel = (AbstractHttp2StreamChannel) stream.attachment;
                if (channel != null) {
                    channel.streamClosed();
                }
                break;
            default:
                // ignore for now
                break;
        }
    }

    // TODO: This is most likely not the best way to expose this, need to think more about it.
    final Http2StreamChannel newOutboundStream() {
        return new Http2MultiplexCodecStreamChannel(newStream(), null);
    }

    @Override
    final void onHttp2FrameStreamException(ChannelHandlerContext ctx, Http2FrameStreamException cause) {
        Http2FrameStream stream = cause.stream();
        AbstractHttp2StreamChannel channel = (AbstractHttp2StreamChannel) ((DefaultHttp2FrameStream) stream).attachment;

        try {
            channel.pipeline().fireExceptionCaught(cause.getCause());
        } finally {
            channel.unsafe().closeForcibly();
        }
    }

    private void onHttp2GoAwayFrame(ChannelHandlerContext ctx, final Http2GoAwayFrame goAwayFrame) {
        try {
            forEachActiveStream(new Http2FrameStreamVisitor() {
                @Override
                public boolean visit(Http2FrameStream stream) {
                    final int streamId = stream.id();
                    AbstractHttp2StreamChannel channel = (AbstractHttp2StreamChannel)
                            ((DefaultHttp2FrameStream) stream).attachment;
                    if (streamId > goAwayFrame.lastStreamId() && connection().local().isValidStreamId(streamId)) {
                        channel.pipeline().fireUserEventTriggered(goAwayFrame.retainedDuplicate());
                    }
                    return true;
                }
            });
        } catch (Http2Exception e) {
            ctx.fireExceptionCaught(e);
            ctx.close();
        }
    }

    /**
     * Notifies any child streams of the read completion.
     */
    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        processPendingReadCompleteQueue();
        channelReadComplete0(ctx);
    }

    private void processPendingReadCompleteQueue() {
        parentReadInProgress = true;
        try {
            // If we have many child channel we can optimize for the case when multiple call flush() in
            // channelReadComplete(...) callbacks and only do it once as otherwise we will end-up with multiple
            // write calls on the socket which is expensive.
            for (;;) {
                AbstractHttp2StreamChannel childChannel = readCompletePendingQueue.poll();
                if (childChannel == null) {
                    break;
                }
                childChannel.fireChildReadComplete();
            }
        } finally {
            parentReadInProgress = false;
            readCompletePendingQueue.clear();
            // We always flush as this is what Http2ConnectionHandler does for now.
            flush0(ctx);
        }
    }
    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        parentReadInProgress = true;
        super.channelRead(ctx, msg);
    }

    @Override
    public final void channelWritabilityChanged(final ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            // While the writability state may change during iterating of the streams we just set all of the streams
            // to writable to not affect fairness. These will be "limited" by their own watermarks in any case.
            forEachActiveStream(AbstractHttp2StreamChannel.WRITABLE_VISITOR);
        }

        super.channelWritabilityChanged(ctx);
    }

    final void flush0(ChannelHandlerContext ctx) {
        flush(ctx);
    }

    private final class Http2MultiplexCodecStreamChannel extends AbstractHttp2StreamChannel {

        Http2MultiplexCodecStreamChannel(DefaultHttp2FrameStream stream, ChannelHandler inboundHandler) {
            super(stream, ++idCount, inboundHandler);
        }

        @Override
        protected boolean isParentReadInProgress() {
            return parentReadInProgress;
        }

        @Override
        protected void addChannelToReadCompletePendingQueue() {
            // If there is no space left in the queue, just keep on processing everything that is already
            // stored there and try again.
            while (!readCompletePendingQueue.offer(this)) {
                processPendingReadCompleteQueue();
            }
        }

        @Override
        protected ChannelHandlerContext parentContext() {
            return ctx;
        }

        @Override
        protected ChannelFuture write0(ChannelHandlerContext ctx, Object msg) {
            ChannelPromise promise = ctx.newPromise();
            Http2MultiplexCodec.this.write(ctx, msg, promise);
            return promise;
        }

        @Override
        protected void flush0(ChannelHandlerContext ctx) {
            Http2MultiplexCodec.this.flush0(ctx);
        }
    }
}
