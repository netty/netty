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
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.util.AttributeKey;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

final class DefaultHttp2StreamChannelBootstrap implements Http2StreamChannelBootstrap {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultHttp2StreamChannelBootstrap.class);
    @SuppressWarnings("unchecked")
    private static final Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Entry[0];
    @SuppressWarnings("unchecked")
    private static final Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Entry[0];

    private final Http2Channel parent;
    // The order in which ChannelOptions are applied is important they may depend on each other for validation
    // purposes.
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<>();
    private ChannelHandler handler;

    DefaultHttp2StreamChannelBootstrap(Http2Channel parent) {
        this.parent = parent;
    }

    @Override
    public <T> Http2StreamChannelBootstrap option(ChannelOption<T> option, T value) {
        checkNotNullWithIAE(option, "option");
        if (value == null) {
            options.remove(option);
        } else {
            options.put(option, value);
        }
        return this;
    }

    @Override
    public <T> Http2StreamChannelBootstrap attr(AttributeKey<T> key, T value) {
        checkNotNullWithIAE(key, "key");
        if (value == null) {
            attrs.remove(key);
        } else {
            attrs.put(key, value);
        }
        return this;
    }

    @Override
    public Http2StreamChannelBootstrap handler(ChannelHandler handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public Future<Http2StreamChannel> create() {
        final Entry<ChannelOption<?>, Object> [] optionArray = options.entrySet().toArray(EMPTY_OPTION_ARRAY);
        final Entry<AttributeKey<?>, Object>[] attributeArray = attrs.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);
        final ChannelHandler handler = this.handler;
        return parent.createStream(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                if (handler != null) {
                    ch.pipeline().addLast(handler);
                }
                setChannelOptions(ch, optionArray);
                setAttributes(ch, attributeArray);
            }
        });
    }

    private static void setChannelOptions(Channel channel, Entry<ChannelOption<?>, Object>[] options) {
        for (Entry<ChannelOption<?>, Object> e: options) {
            setChannelOption(channel, e.getKey(), e.getValue());
        }
    }

    private static void setChannelOption(Channel channel, ChannelOption<?> option, Object value) {
        try {
            @SuppressWarnings("unchecked")
            ChannelOption<Object> opt = (ChannelOption<Object>) option;
            if (!channel.config().setOption(opt, value)) {
                logger.warn("Unknown channel option '{}' for channel '{}'", option, channel);
            }
        } catch (Throwable t) {
            logger.warn("Failed to set channel option '{}' with value '{}' for channel '{}'",
                    option, value, channel, t);
        }
    }

    private static void setAttributes(Channel channel, Entry<AttributeKey<?>, Object>[] options) {
        for (Entry<AttributeKey<?>, Object> e: options) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }
}
