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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;
import io.netty.util.internal.UnstableApi;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.Collections.unmodifiableMap;

/**
 * A class that makes it easy to bootstrap a new HTTP/2 stream as a {@link Channel}.
 *
 * <p>The bootstrap requires a registered parent {@link Channel} with a {@link ChannelPipeline} that contains the
 * {@link Http2MultiplexCodec}.
 *
 * <p>A child channel becomes active as soon as it is registered to an eventloop. Therefore, an active channel does not
 * map to an active HTTP/2 stream immediately. Only once a {@link Http2HeadersFrame} has been sent or received, does
 * the channel map to an active HTTP/2 stream. In case it was not possible to open a new HTTP/2 stream (i.e. due to
 * the maximum number of active streams being exceeded), the child channel receives an exception indicating the reason
 * and is closed immediately thereafter.
 *
 * <p>This class is thread-safe.
 */
// TODO(buchgr): Should we deliver a user event when the stream becomes active? For all stream states?
@UnstableApi
public class Http2StreamChannelBootstrap {

    private volatile Channel parentChannel;
    private volatile Http2MultiplexCodec multiplexCodec;
    private volatile ChannelHandler handler;
    private volatile EventLoopGroup group;
    private final Map<ChannelOption<?>, Object> options;
    private final Map<AttributeKey<?>, Object> attributes;

    // Lock for parentChannel and multiplexCodec
    private final Object lock = new Object();

    public Http2StreamChannelBootstrap() {
        options = new LinkedHashMap<ChannelOption<?>, Object>();
        attributes = new LinkedHashMap<AttributeKey<?>, Object>();
    }

    // Copy constructor
    Http2StreamChannelBootstrap(Http2StreamChannelBootstrap bootstrap0) {
        synchronized (bootstrap0.lock) {
            parentChannel = bootstrap0.parentChannel;
            multiplexCodec = bootstrap0.multiplexCodec;
        }
        handler = bootstrap0.handler;
        group = bootstrap0.group;
        synchronized (bootstrap0.options) {
            options = new LinkedHashMap<ChannelOption<?>, Object>(bootstrap0.options);
        }
        synchronized (bootstrap0.attributes) {
            attributes = new LinkedHashMap<AttributeKey<?>, Object>(bootstrap0.attributes);
        }
    }

    /**
     * Creates a new channel that will eventually map to a local/outbound HTTP/2 stream.
     */
    public ChannelFuture connect() {
        return connect(-1);
    }

    /**
     * Used by the {@link Http2MultiplexCodec} to instantiate incoming/remotely-created streams.
     */
    ChannelFuture connect(int streamId) {
        validateState();

        Channel parentChannel0;
        Http2MultiplexCodec multiplexCodec0;
        synchronized (lock) {
            parentChannel0 = parentChannel;
            multiplexCodec0 = multiplexCodec;
        }
        EventLoopGroup group0 = group;
        group0 = group0 == null ? parentChannel.eventLoop() : group0;
        ChannelHandler handler0 = handler;

        return multiplexCodec0.createStreamChannel(parentChannel0, group0, handler0, options, attributes, streamId);
    }

    /**
     * Sets the parent channel that must have the {@link Http2MultiplexCodec} in its pipeline.
     *
     * @param parent a registered channel with the {@link Http2MultiplexCodec} in its pipeline. This channel will
     *               be the {@link Channel#parent()} of all channels created via {@link #connect()}.
     * @return {@code this}
     */
    public Http2StreamChannelBootstrap parentChannel(Channel parent) {
        synchronized (lock) {
            parentChannel = checkRegistered(checkNotNull(parent, "parent"));
            multiplexCodec = requireMultiplexCodec(parentChannel.pipeline());
        }
        return this;
    }

    /**
     * Sets the channel handler that should be added to the channels's pipeline.
     *
     * @param handler   the channel handler to add to the channel's pipeline. The handler must be
     *                  {@link Sharable}.
     * @return {@code this}
     */
    public Http2StreamChannelBootstrap handler(ChannelHandler handler) {
        this.handler = checkSharable(checkNotNull(handler, "handler"));
        return this;
    }

    /**
     * Sets the {@link EventLoop} to which channels created with this bootstrap are registered.
     *
     * @param group the eventloop or {@code null} if the eventloop of the parent channel should be used.
     * @return {@code this}
     */
    public Http2StreamChannelBootstrap group(EventLoopGroup group) {
        this.group = group;
        return this;
    }

    /**
     * Specify {@link ChannelOption}s to be set on newly created channels. An option can be removed by specifying a
     * value of {@code null}.
     */
    public <T> Http2StreamChannelBootstrap option(ChannelOption<T> option, T value) {
        checkNotNull(option, "option must not be null");
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
     * Specify attributes with an initial value to be set on newly created channels. An attribute can be removed by
     * specifying a value of {@code null}.
     */
    public <T> Http2StreamChannelBootstrap attr(AttributeKey<T> key, T value) {
        checkNotNull(key, "key must not be null");
        if (value == null) {
            synchronized (attributes) {
                attributes.remove(key);
            }
        } else {
            synchronized (attributes) {
                attributes.put(key, value);
            }
        }
        return this;
    }

    public Channel parentChannel() {
        return parentChannel;
    }

    public ChannelHandler handler() {
        return handler;
    }

    public EventLoopGroup group() {
        return group;
    }

    public Map<ChannelOption<?>, Object> options() {
        synchronized (options) {
            return unmodifiableMap(new LinkedHashMap<ChannelOption<?>, Object>(options));
        }
    }

    public Map<AttributeKey<?>, Object> attributes() {
        synchronized (attributes) {
            return unmodifiableMap(new LinkedHashMap<AttributeKey<?>, Object>(attributes));
        }
    }

    private void validateState() {
        checkNotNull(handler, "handler must be set");
        checkNotNull(parentChannel, "parent channel must be set");
        checkNotNull(multiplexCodec, "multiplex codec must be set");
    }

    private static Channel checkRegistered(Channel channel) {
        if (!channel.isRegistered()) {
            throw new IllegalArgumentException("The channel must be registered to an eventloop.");
        }
        return channel;
    }

    private static ChannelHandler checkSharable(ChannelHandler handler) {
        if (!handler.getClass().isAnnotationPresent(Sharable.class)) {
            throw new IllegalArgumentException("The handler must be Sharable");
        }
        return handler;
    }

    private static Http2MultiplexCodec requireMultiplexCodec(ChannelPipeline pipeline) {
        ChannelHandlerContext ctx = pipeline.context(Http2MultiplexCodec.class);
        if (ctx == null) {
            throw new IllegalArgumentException(Http2MultiplexCodec.class.getSimpleName()
                                               + " was not found in the channel pipeline.");
        }
        return (Http2MultiplexCodec) ctx.handler();
    }
}
