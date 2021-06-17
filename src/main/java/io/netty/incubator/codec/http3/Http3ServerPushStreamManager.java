/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamChannelBootstrap;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.UnaryOperator;

import static io.netty.incubator.codec.http3.Http3.maxPushIdReceived;
import static io.netty.incubator.codec.http3.Http3CodecUtils.connectionError;
import static io.netty.incubator.codec.http3.Http3ErrorCode.H3_ID_ERROR;
import static io.netty.util.internal.PlatformDependent.newConcurrentHashMap;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

/**
 * A manager for <a href="https://quicwg.org/base-drafts/draft-ietf-quic-http.html#name-push-streams">push streams</a>
 * for a server. New push streams can be initiated using the various {@code newPushStream} methods. It is required to
 * add the {@link ChannelHandler} returned from {@link #controlStreamListener()} to the {@link QuicChannel} associated
 * with this manager.
 */
public final class Http3ServerPushStreamManager {
    private static final AtomicLongFieldUpdater<Http3ServerPushStreamManager> nextIdUpdater =
            newUpdater(Http3ServerPushStreamManager.class, "nextId");
    private static final Object CANCELLED_STREAM = new Object();
    private static final Object PUSH_ID_GENERATED = new Object();
    private static final Object AWAITING_STREAM_ESTABLISHMENT = new Object();

    private final QuicChannel channel;
    private final ConcurrentMap<Long, Object> pushStreams;
    private final ChannelInboundHandler controlStreamListener;

    private volatile long nextId;

    /**
     * Creates a new instance.
     *
     * @param channel for which this manager is created.
     */
    public Http3ServerPushStreamManager(QuicChannel channel) {
        this(channel, 8);
    }

    /**
     * Creates a new instance.
     *
     * @param channel for which this manager is created.
     * @param initialPushStreamsCountHint a hint for the number of push streams that may be created.
     */
    public Http3ServerPushStreamManager(QuicChannel channel, int initialPushStreamsCountHint) {
        this.channel = requireNonNull(channel, "channel");
        pushStreams = newConcurrentHashMap(initialPushStreamsCountHint);
        controlStreamListener = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof Http3CancelPushFrame) {
                    final long pushId = ((Http3CancelPushFrame) msg).id();
                    if (pushId >= nextId) {
                        connectionError(ctx, H3_ID_ERROR, "CANCEL_PUSH id greater than the last known id", true);
                        return;
                    }

                    pushStreams.computeIfPresent(pushId, (id, existing) -> {
                        if (existing == AWAITING_STREAM_ESTABLISHMENT) {
                            return CANCELLED_STREAM;
                        }
                        if (existing == PUSH_ID_GENERATED) {
                            throw new IllegalStateException("Unexpected push stream state " + existing +
                                    " for pushId: " + id);
                        }
                        assert existing instanceof QuicStreamChannel;
                        ((QuicStreamChannel) existing).close();
                        // remove the push stream from the map.
                        return null;
                    });
                }
                ReferenceCountUtil.release(msg);
            }
        };
    }

    /**
     * Returns {@code true} if server push is allowed at this point.
     *
     * @return {@code true} if server push is allowed at this point.
     */
    public boolean isPushAllowed() {
        return isPushAllowed(maxPushIdReceived(channel));
    }

    /**
     * Reserves a push ID to be used to create a new push stream subsequently. A push ID can only be used to create
     * exactly one push stream.
     *
     * @return Next push ID.
     * @throws IllegalStateException If it is not allowed to create any more push streams on the associated
     * {@link QuicChannel}. Use {@link #isPushAllowed()} to check if server push is allowed.
     */
    public long reserveNextPushId() {
        final long maxPushId = maxPushIdReceived(channel);
        if (isPushAllowed(maxPushId)) {
            return nextPushId();
        }
        throw new IllegalStateException("MAX allowed push ID: " + maxPushId + ", next push ID: " + nextId);
    }

    /**
     * Returns a new HTTP/3 push-stream that will use the given {@link ChannelHandler}
     * to dispatch {@link Http3PushStreamFrame}s too. The needed HTTP/3 codecs are automatically added to the
     * pipeline as well.
     *
     * @param pushId for the push stream. This MUST be obtained using {@link #reserveNextPushId()}.
     * @param handler the {@link ChannelHandler} to add. Can be {@code null}.
     * @return the {@link Future} that will be notified once the push-stream was opened.
     */
    public Future<QuicStreamChannel> newPushStream(long pushId, ChannelHandler handler) {
        final Promise<QuicStreamChannel> promise = channel.eventLoop().newPromise();
        newPushStream(pushId, handler, promise);
        return promise;
    }

    /**
     * Returns a new HTTP/3 push-stream that will use the given {@link ChannelHandler}
     * to dispatch {@link Http3PushStreamFrame}s too. The needed HTTP/3 codecs are automatically added to the
     * pipeline as well.
     *
     * @param pushId for the push stream. This MUST be obtained using {@link #reserveNextPushId()}.
     * @param handler the {@link ChannelHandler} to add. Can be {@code null}.
     * @param promise to indicate creation of the push stream.
     */
    public void newPushStream(long pushId, ChannelHandler handler, Promise<QuicStreamChannel> promise) {
        validatePushId(pushId);
        channel.createStream(QuicStreamType.UNIDIRECTIONAL, pushStreamInitializer(pushId, handler), promise);
        setupCancelPushIfStreamCreationFails(pushId, promise, channel);
    }

    /**
     * Returns a new HTTP/3 push-stream that will use the given {@link ChannelHandler}
     * to dispatch {@link Http3PushStreamFrame}s too. The needed HTTP/3 codecs are automatically added to the
     * pipeline as well.
     *
     * @param pushId for the push stream. This MUST be obtained using {@link #reserveNextPushId()}.
     * @param handler the {@link ChannelHandler} to add. Can be {@code null}.
     * @param bootstrapConfigurator {@link UnaryOperator} to configure the {@link QuicStreamChannelBootstrap} used.
     * @param promise to indicate creation of the push stream.
     */
    public void newPushStream(long pushId, ChannelHandler handler,
                              UnaryOperator<QuicStreamChannelBootstrap> bootstrapConfigurator,
                              Promise<QuicStreamChannel> promise) {
        validatePushId(pushId);
        QuicStreamChannelBootstrap bootstrap = bootstrapConfigurator.apply(channel.newStreamBootstrap());
        bootstrap.type(QuicStreamType.UNIDIRECTIONAL)
                .handler(pushStreamInitializer(pushId, handler))
                .create(promise);
        setupCancelPushIfStreamCreationFails(pushId, promise, channel);
    }

    /**
     * A {@link ChannelInboundHandler} to be added to the {@link QuicChannel} associated with this
     * {@link Http3ServerPushStreamManager} to listen to control stream frames.
     *
     * @return {@link ChannelInboundHandler} to be added to the {@link QuicChannel} associated with this
     * {@link Http3ServerPushStreamManager} to listen to control stream frames.
     */
    public ChannelInboundHandler controlStreamListener() {
        return controlStreamListener;
    }

    private boolean isPushAllowed(long maxPushId) {
        return nextId <= maxPushId;
    }

    private long nextPushId() {
        final long pushId = nextIdUpdater.getAndIncrement(this);
        pushStreams.put(pushId, PUSH_ID_GENERATED);
        return pushId;
    }

    private void validatePushId(long pushId) {
        if (!pushStreams.replace(pushId, PUSH_ID_GENERATED, AWAITING_STREAM_ESTABLISHMENT)) {
            throw new IllegalArgumentException("Unknown push ID: " + pushId);
        }
    }

    private Http3PushStreamServerInitializer pushStreamInitializer(long pushId, ChannelHandler handler) {
        final Http3PushStreamServerInitializer initializer;
        if (handler instanceof Http3PushStreamServerInitializer) {
            initializer = (Http3PushStreamServerInitializer) handler;
        } else {
            initializer = null;
        }
        return new Http3PushStreamServerInitializer(pushId) {
            @Override
            protected void initPushStream(QuicStreamChannel ch) {
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    private boolean stateUpdated;

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        if (!stateUpdated) {
                            updatePushStreamsMap();
                        }
                    }

                    @Override
                    public void handlerAdded(ChannelHandlerContext ctx) {
                        if (!stateUpdated && ctx.channel().isActive()) {
                            updatePushStreamsMap();
                        }
                    }

                    private void updatePushStreamsMap() {
                        assert !stateUpdated;
                        stateUpdated = true;
                        pushStreams.compute(pushId, (id, existing) -> {
                            if (existing == AWAITING_STREAM_ESTABLISHMENT) {
                                return ch;
                            }
                            if (existing == CANCELLED_STREAM) {
                                ch.close();
                                return null; // remove push stream.
                            }
                            throw new IllegalStateException("Unexpected push stream state " +
                                    existing + " for pushId: " + id);
                        });
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            pushStreams.remove(pushId);
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                });
                if (initializer != null) {
                    initializer.initPushStream(ch);
                } else if (handler != null) {
                    ch.pipeline().addLast(handler);
                }
            }
        };
    }

    private static void setupCancelPushIfStreamCreationFails(long pushId, Future<QuicStreamChannel> future,
                                                             QuicChannel channel) {
        if (future.isDone()) {
            sendCancelPushIfFailed(future, pushId, channel);
        } else {
            future.addListener(f -> sendCancelPushIfFailed(future, pushId, channel));
        }
    }

    private static void sendCancelPushIfFailed(Future<QuicStreamChannel> future, long pushId, QuicChannel channel) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-http.html#name-cancel_push
        // If we can not establish the stream, we can not send the promised push response, so send a CANCEL_PUSH
        if (!future.isSuccess()) {
            final QuicStreamChannel localControlStream = Http3.getLocalControlStream(channel);
            assert localControlStream != null;
            localControlStream.writeAndFlush(new DefaultHttp3CancelPushFrame(pushId));
        }
    }
}
