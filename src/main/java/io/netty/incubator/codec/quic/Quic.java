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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Map;

public final class Quic {
    @SuppressWarnings("unchecked")
    static final Map.Entry<ChannelOption<?>, Object>[] EMPTY_OPTION_ARRAY = new Map.Entry[0];
    @SuppressWarnings("unchecked")
    static final Map.Entry<AttributeKey<?>, Object>[] EMPTY_ATTRIBUTE_ARRAY = new Map.Entry[0];

    static final int MAX_DATAGRAM_SIZE = 1350;

    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;

        if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
            cause = new UnsupportedOperationException(
                    "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
        } else {
            String version = Quiche.quiche_version();
            assert version != null;
        }

        UNAVAILABILITY_CAUSE = cause;
    }

    /**
     * Returns {@code true} if and only if the <a href="https://netty.io/wiki/native-transports.html">{@code
     * netty-transport-native-epoll}</a> is available.
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Ensure that <a href="https://netty.io/wiki/native-transports.html">{@code netty-transport-native-epoll}</a> is
     * available.
     *
     * @throws UnsatisfiedLinkError if unavailable
     */
    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    /**
     * Returns the cause of unavailability of <a href="https://netty.io/wiki/native-transports.html">
     * {@code netty-transport-native-epoll}</a>.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    /**
     * Return a {@link QuicConnectionIdGenerator} which randomly generates new connection ids.
     */
    public static QuicConnectionIdGenerator randomGenerator() {
        return SecureRandomQuicConnectionIdGenerator.INSTANCE;
    }

    private static final class SecureRandomQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
        private static final SecureRandom RANDOM = new SecureRandom();

        public static final QuicConnectionIdGenerator INSTANCE = new SecureRandomQuicConnectionIdGenerator();

        private SecureRandomQuicConnectionIdGenerator() { }

        @Override
        public ByteBuffer newId() {
            return newId(maxConnectionIdLength());
        }

        @Override
        public ByteBuffer newId(int length) {
            if (length > maxConnectionIdLength()) {
                throw new IllegalArgumentException();
            }
            byte[] bytes = new byte[length];
            RANDOM.nextBytes(bytes);
            return ByteBuffer.wrap(bytes);
        }

        @Override
        public ByteBuffer newId(ByteBuffer buffer, int length) {
            return newId(length);
        }

        @Override
        public int maxConnectionIdLength() {
            return Quiche.QUICHE_MAX_CONN_ID_LEN;
        }
    }

    static Map.Entry<ChannelOption<?>, Object>[] optionsArray(Map<ChannelOption<?>, Object> opts) {
        return opts.entrySet().toArray(EMPTY_OPTION_ARRAY);
    }

    static Map.Entry<AttributeKey<?>, Object>[] attributesArray(Map<AttributeKey<?>, Object> attributes) {
        return attributes.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);
    }

    private static void setAttributes(Channel channel, Map.Entry<AttributeKey<?>, Object>[] attrs) {
        for (Map.Entry<AttributeKey<?>, Object> e: attrs) {
            @SuppressWarnings("unchecked")
            AttributeKey<Object> key = (AttributeKey<Object>) e.getKey();
            channel.attr(key).set(e.getValue());
        }
    }

    private static void setChannelOptions(
            Channel channel, Map.Entry<ChannelOption<?>, Object>[] options, InternalLogger logger) {
        for (Map.Entry<ChannelOption<?>, Object> e: options) {
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

    /**
     * Allow to specify a {@link ChannelOption} which is used for the {@link QuicStreamChannel} instances once they got
     * created. Use a value of {@code null} to remove a previous set {@link ChannelOption}.
     */
    static <T> void updateOptions(Map<ChannelOption<?>, Object> options, ChannelOption<T> option, T value) {
        ObjectUtil.checkNotNull(option, "option");
        if (value == null) {
            options.remove(option);
        } else {
            options.put(option, value);
        }
    }

    /**
     * Allow to specify an initial attribute of the newly created {@link QuicStreamChannel}. If the {@code value} is
     * {@code null}, the attribute of the specified {@code key} is removed.
     */
    static <T> void updateAttributes(Map<AttributeKey<?>, Object> attributes, AttributeKey<T> key, T value) {
        ObjectUtil.checkNotNull(key, "key");
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    static void setupChannel(Channel ch, Map.Entry<ChannelOption<?>, Object>[] options,
                             Map.Entry<AttributeKey<?>, Object>[] attrs, ChannelHandler handler,
                             InternalLogger logger) {
        Quic.setChannelOptions(ch, options, logger);
        Quic.setAttributes(ch, attrs);
        if (handler != null) {
            ch.pipeline().addLast(handler);
        }
    }

    private Quic() { }
}
