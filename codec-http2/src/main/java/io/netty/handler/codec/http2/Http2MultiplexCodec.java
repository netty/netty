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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.IntObjectMap.PrimitiveEntry;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.netty.handler.codec.http2.AbstractHttp2StreamChannel.CLOSE_MESSAGE;
import static io.netty.handler.codec.http2.Http2CodecUtil.isOutboundStream;
import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static java.lang.String.format;

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
 * messages.
 *
 * <p>Outbound streams are supported via the {@link Http2StreamChannelBootstrap}.
 *
 * <p>{@link ChannelConfig#setMaxMessagesPerRead(int)} and {@link ChannelConfig#setAutoRead(boolean)} are supported.
 */
@UnstableApi
public final class Http2MultiplexCodec extends ChannelDuplexHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2MultiplexCodec.class);

    private final Http2StreamChannelBootstrap bootstrap;

    private final List<Http2StreamChannel> channelsToFireChildReadComplete = new ArrayList<Http2StreamChannel>();
    private final boolean server;
    private ChannelHandlerContext ctx;
    private volatile Runnable flushTask;

    private final IntObjectMap<Http2StreamChannel> childChannels = new IntObjectHashMap<Http2StreamChannel>();

    /**
     * Construct a new handler whose child channels run in a different event loop.
     *
     * @param server {@code true} this is a server
     * @param bootstrap bootstrap used to instantiate child channels for remotely-created streams.
     */
    public Http2MultiplexCodec(boolean server, Http2StreamChannelBootstrap bootstrap) {
        if (bootstrap.parentChannel() != null) {
            throw new IllegalStateException("The parent channel must not be set on the bootstrap.");
        }
        this.server = server;
        this.bootstrap = new Http2StreamChannelBootstrap(bootstrap);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        bootstrap.parentChannel(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof StreamException)) {
            ctx.fireExceptionCaught(cause);
            return;
        }

        StreamException streamEx = (StreamException) cause;
        try {
            Http2StreamChannel childChannel = childChannels.get(streamEx.streamId());
            if (childChannel != null) {
                childChannel.pipeline().fireExceptionCaught(streamEx);
            } else {
                logger.warn(format("Exception caught for unknown HTTP/2 stream '%d'", streamEx.streamId()),
                            streamEx);
            }
        } finally {
            onStreamClosed(streamEx.streamId());
        }
    }

    // Override this to signal it will never throw an exception.
    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof Http2Frame)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (msg instanceof Http2StreamFrame) {
            Http2StreamFrame frame = (Http2StreamFrame) msg;
            int streamId = frame.streamId();
            Http2StreamChannel childChannel = childChannels.get(streamId);
            if (childChannel == null) {
                // TODO: Combine with DefaultHttp2ConnectionDecoder.shouldIgnoreHeadersOrDataFrame logic.
                ReferenceCountUtil.release(msg);
                throw new StreamException(streamId, STREAM_CLOSED, format("Received %s frame for an unknown stream %d",
                                                                          frame.name(), streamId));
            }
            fireChildReadAndRegister(childChannel, frame);
        } else if (msg instanceof Http2GoAwayFrame) {
            Http2GoAwayFrame goAwayFrame = (Http2GoAwayFrame) msg;
            for (PrimitiveEntry<Http2StreamChannel> entry : childChannels.entries()) {
                Http2StreamChannel childChannel = entry.value();
                int streamId = entry.key();
                if (streamId > goAwayFrame.lastStreamId() && isOutboundStream(server, streamId)) {
                    childChannel.pipeline().fireUserEventTriggered(goAwayFrame.retainedDuplicate());
                }
            }
            goAwayFrame.release();
        } else {
            // It's safe to release, as UnsupportedMessageTypeException just calls msg.getClass()
            ReferenceCountUtil.release(msg);
            throw new UnsupportedMessageTypeException(msg);
        }
    }

    private void fireChildReadAndRegister(Http2StreamChannel childChannel, Http2StreamFrame frame) {
        // Can't use childChannel.fireChannelRead() as it would fire independent of whether
        // channel.read() had been called.
        childChannel.fireChildRead(frame);
        if (!childChannel.inStreamsToFireChildReadComplete) {
            channelsToFireChildReadComplete.add(childChannel);
            childChannel.inStreamsToFireChildReadComplete = true;
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof Http2StreamActiveEvent) {
            Http2StreamActiveEvent activeEvent = (Http2StreamActiveEvent) evt;
            onStreamActive(activeEvent.streamId(), activeEvent.headers());
        } else if (evt instanceof Http2StreamClosedEvent) {
            onStreamClosed(((Http2StreamClosedEvent) evt).streamId());
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    private void onStreamActive(int streamId, Http2HeadersFrame headersFrame) {
        final Http2StreamChannel childChannel;
        if (isOutboundStream(server, streamId)) {
            if (!(headersFrame instanceof ChannelCarryingHeadersFrame)) {
                throw new IllegalArgumentException("needs to be wrapped");
            }
            childChannel = ((ChannelCarryingHeadersFrame) headersFrame).channel();
            childChannel.streamId(streamId);
        } else {
            ChannelFuture future = bootstrap.connect(streamId);
            childChannel = (Http2StreamChannel) future.channel();
        }

        Http2StreamChannel existing = childChannels.put(streamId, childChannel);
        assert existing == null;
    }

    private void onStreamClosed(int streamId) {
        final Http2StreamChannel childChannel = childChannels.remove(streamId);
        if (childChannel != null) {
            final EventLoop eventLoop = childChannel.eventLoop();
            if (eventLoop.inEventLoop()) {
                onStreamClosed0(childChannel);
            } else {
                eventLoop.execute(new Runnable() {
                    @Override
                    public void run() {
                        onStreamClosed0(childChannel);
                    }
                });
            }
        }
    }

    private void onStreamClosed0(Http2StreamChannel childChannel) {
        assert childChannel.eventLoop().inEventLoop();

        childChannel.onStreamClosedFired = true;
        childChannel.fireChildRead(CLOSE_MESSAGE);
    }

    void flushFromStreamChannel() {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            flush(ctx);
        } else {
            Runnable task = flushTask;
            if (task == null) {
                task = flushTask = new Runnable() {
                    @Override
                    public void run() {
                        flush(ctx);
                    }
                };
            }
            executor.execute(task);
        }
    }

    void writeFromStreamChannel(Object msg, boolean flush) {
        writeFromStreamChannel(msg, ctx.newPromise(), flush);
    }

    void writeFromStreamChannel(final Object msg, final ChannelPromise promise, final boolean flush) {
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            writeFromStreamChannel0(msg, flush, promise);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        writeFromStreamChannel0(msg, flush, promise);
                    }
                });
            } catch (Throwable cause) {
                promise.setFailure(cause);
            }
        }
    }

    private void writeFromStreamChannel0(Object msg, boolean flush, ChannelPromise promise) {
        try {
            write(ctx, msg, promise);
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
        if (flush) {
            flush(ctx);
        }
    }

    /**
     * Notifies any child streams of the read completion.
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        for (int i = 0; i < channelsToFireChildReadComplete.size(); i++) {
            Http2StreamChannel childChannel = channelsToFireChildReadComplete.get(i);
            // Clear early in case fireChildReadComplete() causes it to need to be re-processed
            childChannel.inStreamsToFireChildReadComplete = false;
            childChannel.fireChildReadComplete();
        }
        channelsToFireChildReadComplete.clear();
    }

    ChannelFuture createStreamChannel(Channel parentChannel, EventLoopGroup group, ChannelHandler handler,
                                              Map<ChannelOption<?>, Object> options,
                                              Map<AttributeKey<?>, Object> attrs,
                                              int streamId) {
        final Http2StreamChannel channel = new Http2StreamChannel(parentChannel);
        if (isStreamIdValid(streamId)) {
            assert !isOutboundStream(server, streamId);
            assert ctx.channel().eventLoop().inEventLoop();
            channel.streamId(streamId);
        }
        channel.pipeline().addLast(handler);

        initOpts(channel, options);
        initAttrs(channel, attrs);

        ChannelFuture future = group.register(channel);
        // Handle any errors that occurred on the local thread while registering. Even though
        // failures can happen after this point, they will be handled by the channel by closing the
        // channel.
        if (future.cause() != null) {
            if (channel.isRegistered()) {
                channel.close();
            } else {
                channel.unsafe().closeForcibly();
            }
        }
        return future;
    }

    @SuppressWarnings("unchecked")
    private static void initOpts(Channel channel, Map<ChannelOption<?>, Object> opts) {
        if (opts != null) {
            for (Entry<ChannelOption<?>, Object> e: opts.entrySet()) {
                try {
                    if (!channel.config().setOption((ChannelOption<Object>) e.getKey(), e.getValue())) {
                        logger.warn("Unknown channel option: " + e);
                    }
                } catch (Throwable t) {
                    logger.warn("Failed to set a channel option: " + channel, t);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void initAttrs(Channel channel, Map<AttributeKey<?>, Object> attrs) {
        if (attrs != null) {
            for (Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
    }

    final class Http2StreamChannel extends AbstractHttp2StreamChannel implements ChannelFutureListener {
        boolean onStreamClosedFired;

        /**
         * {@code true} if stream is in {@link Http2MultiplexCodec#channelsToFireChildReadComplete}.
         */
        boolean inStreamsToFireChildReadComplete;

        Http2StreamChannel(Channel parentChannel) {
            super(parentChannel);
        }

        @Override
        protected void doClose() throws Exception {
            if (!onStreamClosedFired && isStreamIdValid(streamId())) {
                Http2StreamFrame resetFrame = new DefaultHttp2ResetFrame(Http2Error.CANCEL).streamId(streamId());
                writeFromStreamChannel(resetFrame, true);
            }
            super.doClose();
        }

        @Override
        protected void doWrite(Object msg) {
            if (!(msg instanceof Http2StreamFrame)) {
                ReferenceCountUtil.release(msg);
                throw new IllegalArgumentException("Message must be an Http2StreamFrame: " + msg);
            }
            Http2StreamFrame frame = (Http2StreamFrame) msg;
            ChannelPromise promise = ctx.newPromise();
            if (isStreamIdValid(frame.streamId())) {
                ReferenceCountUtil.release(frame);
                throw new IllegalArgumentException("Stream id must not be set on the frame. Was: " + frame.streamId());
            }
            if (!isStreamIdValid(streamId())) {
                if (!(frame instanceof Http2HeadersFrame)) {
                    throw new IllegalArgumentException("The first frame must be a headers frame. Was: " + frame.name());
                }
                frame = new ChannelCarryingHeadersFrame((Http2HeadersFrame) frame, this);
                // Handle errors on stream creation
                promise.addListener(this);
            } else {
                frame.streamId(streamId());
            }
            writeFromStreamChannel(frame, promise, false);
        }

        @Override
        protected void doWriteComplete() {
            flushFromStreamChannel();
        }

        @Override
        protected EventExecutor preferredEventExecutor() {
            return ctx.executor();
        }

        @Override
        protected void bytesConsumed(final int bytes) {
            ctx.write(new DefaultHttp2WindowUpdateFrame(bytes).streamId(streamId()));
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            Throwable cause = future.cause();
            if (cause != null) {
                pipeline().fireExceptionCaught(cause);
                close();
            }
        }
    }

    /**
     * Wraps the first {@link Http2HeadersFrame} of local/outbound stream. This allows us to get to the child channel
     * when receiving the {@link Http2StreamActiveEvent} from the frame codec. See {@link #onStreamActive}.
     */
    private static final class ChannelCarryingHeadersFrame implements Http2HeadersFrame {

        private final Http2HeadersFrame frame;
        private final Http2StreamChannel childChannel;

        ChannelCarryingHeadersFrame(Http2HeadersFrame frame, Http2StreamChannel childChannel) {
            this.frame = frame;
            this.childChannel = childChannel;
        }

        @Override
        public Http2Headers headers() {
            return frame.headers();
        }

        @Override
        public boolean isEndStream() {
            return frame.isEndStream();
        }

        @Override
        public int padding() {
            return frame.padding();
        }

        @Override
        public Http2StreamFrame streamId(int streamId) {
            return frame.streamId(streamId);
        }

        @Override
        public int streamId() {
            return frame.streamId();
        }

        @Override
        public String name() {
            return frame.name();
        }

        Http2StreamChannel channel() {
            return childChannel;
        }
    }
}
