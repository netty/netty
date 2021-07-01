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

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

import static io.netty5.handler.codec.h2new.Http2ServerCodecBuilder.FLOW_CONTROLLED_BYTES_DISTRIBUTOR_ATTRIBUTE_KEY;

final class Http2ControlStreamInitializer extends ChannelHandlerAdapter {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2ControlStreamInitializer.class);
    static final AttributeKey<ControlStream>
            CONTROL_STREAM_ATTRIBUTE_KEY = AttributeKey.newInstance("_netty.v5.h2.control.stream");

    private final Http2Settings initialSettings;
    private final DefaultHttp2Channel h2channelIfPresent;
    private final ChannelFlowControlledBytesDistributor distributor;
    private final boolean isServer;

    private boolean initialized;
    private long maxConcurrentStreams;
    private ControlStream controlStream;

    Http2ControlStreamInitializer(Http2Settings initialSettings,
                                  ChannelFlowControlledBytesDistributor flowControlledBytesDistributor,
                                  boolean isServer) {
        this(initialSettings, null, flowControlledBytesDistributor, isServer);
    }

    Http2ControlStreamInitializer(Http2Settings initialSettings, DefaultHttp2Channel h2channel,
                                  ChannelFlowControlledBytesDistributor flowControlledBytesDistributor,
                                  boolean isServer) {
        this.initialSettings = initialSettings;
        h2channelIfPresent = h2channel;
        this.distributor = flowControlledBytesDistributor;
        this.isServer = isServer;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initialize(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2SettingsFrame) {
            Http2SettingsFrame settingsFrame = (Http2SettingsFrame) msg;
            final Long maxConcurrentStreams = settingsFrame.settings().maxConcurrentStreams();
            if (maxConcurrentStreams != null) {
                final long oldValue = this.maxConcurrentStreams;
                this.maxConcurrentStreams = maxConcurrentStreams;
                if (oldValue != maxConcurrentStreams && controlStream != null) {
                    controlStream.maxConcurrentStreamsIncreased(maxConcurrentStreams - oldValue);
                }
            }
        }
        ctx.fireChannelRead(msg);
    }

    private void initialize(ChannelHandlerContext ctx) {
        if (initialized) {
            return;
        }

        initialized = true;
        trySetAttribute(ctx, FLOW_CONTROLLED_BYTES_DISTRIBUTOR_ATTRIBUTE_KEY, distributor);
        if (h2channelIfPresent == null) {
            sendInitialSettings(ctx.channel());
        } else {
            controlStream = new ControlStream();
            trySetAttribute(ctx, CONTROL_STREAM_ATTRIBUTE_KEY, controlStream);
            controlStream.channel().pipeline().addLast(new ChannelHandlerAdapter() {
                private boolean settingsSent;

                @Override
                public void channelRegistered(ChannelHandlerContext ctx) {
                    if (!settingsSent) {
                        settingsSent = true;
                        sendInitialSettings(ctx.channel());
                    }
                    ctx.fireChannelRegistered();
                }
            });
        }
    }

    private static <T> void trySetAttribute(ChannelHandlerContext ctx, AttributeKey<T> key, T value) {
        final T removed = ctx.channel().attr(key).setIfAbsent(value);
        if (removed != null) {
            logger.debug("Failed to set attribute {}, attribute already exists with value: {}.", key, removed);
        }
    }

    private void sendInitialSettings(Channel channel) {
        channel.writeAndFlush(new DefaultHttp2SettingsFrame(initialSettings))
                .addListener(channel, ChannelFutureListeners.CLOSE_ON_FAILURE);
    }

    final class ControlStream {
        private long activeStreamsCount;
        private final DefaultHttp2StreamChannel channel;
        private final Deque<Promise<Void>> waitingReserve = new ArrayDeque<>();

        ControlStream() {
            channel = new DefaultHttp2StreamChannel(h2channelIfPresent, isServer, 0);
        }

        boolean tryReserveActiveStream() {
            assert channel.executor().inEventLoop();

            if (activeStreamsCount < maxConcurrentStreams) {
                ++activeStreamsCount;
                return true;
            }
            return false;
        }

        void reservedActiveStreamClosed() {
            assert channel.executor().inEventLoop();
            activeStreamsCount--;
        }

        Future<Void> reserveActiveStreamWhenAvailable() {
            assert channel.executor().inEventLoop();
            if (tryReserveActiveStream()) {
                return channel.newSucceededFuture();
            }
            final Promise<Void> promise = channel.executor().newPromise();
            waitingReserve.addLast(promise);
            return promise.asFuture();
        }

        DefaultHttp2StreamChannel channel() {
            return channel;
        }

        void maxConcurrentStreamsIncreased(long increment) {
            for (int i = 0; i < increment; i++) {
                final Promise<Void> promise = waitingReserve.pollFirst();
                if (promise == null) {
                    return;
                }
                promise.setSuccess(null);
            }
        }
    }
}
