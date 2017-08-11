/*
 * Copyright 2017 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;

@UnstableApi
public final class Http2StreamChannelBootstrap {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2StreamChannelBootstrap.class);

    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();
    private final Map<AttributeKey<?>, Object> attrs = new LinkedHashMap<AttributeKey<?>, Object>();
    private final Channel channel;
    private volatile ChannelHandler handler;

    public Http2StreamChannelBootstrap(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
    }

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link Http2StreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    @SuppressWarnings("unchecked")
    public <T> Http2StreamChannelBootstrap option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        if (value == null) {
            synchronized (options) {
                options.remove(option);
            }
        } else {
            synchronized (options) {
                options.put(option, value);
            }
        }
        return this;
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link Http2StreamChannel}.  If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    @SuppressWarnings("unchecked")
    public <T> Http2StreamChannelBootstrap attr(AttributeKey<T> key, T value) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            synchronized (attrs) {
                attrs.remove(key);
            }
        } else {
            synchronized (attrs) {
                attrs.put(key, value);
            }
        }
        return this;
    }

    /**
     * the {@link ChannelHandler} to use for serving the requests.
     */
    @SuppressWarnings("unchecked")
    public Http2StreamChannelBootstrap handler(ChannelHandler handler) {
        this.handler = ObjectUtil.checkNotNull(handler, "handler");
        return this;
    }

    public Future<Http2StreamChannel> open() {
        return open(channel.eventLoop().<Http2StreamChannel>newPromise());
    }

    public Future<Http2StreamChannel> open(final Promise<Http2StreamChannel> promise) {
        final ChannelHandlerContext ctx = channel.pipeline().context(Http2MultiplexCodec.class);
        if (ctx == null) {
            if (channel.isActive()) {
                promise.setFailure(new IllegalStateException(StringUtil.simpleClassName(Http2MultiplexCodec.class) +
                        " must be in the ChannelPipeline of Channel " + channel));
            } else {
                promise.setFailure(new ClosedChannelException());
            }
        } else {
            EventExecutor executor = ctx.executor();
            if (executor.inEventLoop()) {
                open0(ctx, promise);
            } else {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        open0(ctx, promise);
                    }
                });
            }
        }
        return promise;
    }

    public void open0(ChannelHandlerContext ctx, final Promise<Http2StreamChannel> promise) {
        assert ctx.executor().inEventLoop();
        final Http2StreamChannel streamChannel = ((Http2MultiplexCodec) ctx.handler()).newOutboundStream();
        try {
            init(streamChannel);
        } catch (Exception e) {
            streamChannel.unsafe().closeForcibly();
            promise.setFailure(e);
            return;
        }

        ChannelFuture future = ctx.channel().eventLoop().register(streamChannel);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    promise.setSuccess(streamChannel);
                } else if (future.isCancelled()) {
                    promise.cancel(false);
                } else {
                    if (streamChannel.isRegistered()) {
                        streamChannel.close();
                    } else {
                        streamChannel.unsafe().closeForcibly();
                    }

                    promise.setFailure(future.cause());
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void init(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        ChannelHandler handler = this.handler;
        if (handler != null) {
            p.addLast(handler);
        }
        synchronized (options) {
            setChannelOptions(channel, options, logger);
        }

        synchronized (attrs) {
            for (Map.Entry<AttributeKey<?>, Object> e: attrs.entrySet()) {
                channel.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }
        }
    }

    private static void setChannelOptions(
            Channel channel, Map<ChannelOption<?>, Object> options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options.entrySet()) {
            setChannelOption(channel, e.getKey(), e.getValue(), logger);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setChannelOption(
            Channel channel, ChannelOption<?> option, Object value, InternalLogger logger) {
        try {
            if (!channel.config().setOption((ChannelOption<Object>) option, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn(
                    "Failed to set channel option '{}' with value '{}' for channel '{}'", option, value, channel, t);
        }
    }
}
