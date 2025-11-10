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
package io.netty.handler.codec.http3;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * See <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4">SETTINGS</a>.
 */
public interface Http3SettingsFrame extends Http3ControlStreamFrame, Iterable<Map.Entry<Long, Long>> {

    /**
     * @deprecated Use {@link Http3Settings#HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY} instead.
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-5">
     * SETTINGS_QPACK_MAX_TABLE_CAPACITY</a>.
     */
    @Deprecated
    long HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY = Http3Settings.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;

    /**
     * @deprecated Use {@link Http3Settings#HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS} instead.
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-5">
     * SETTINGS_QPACK_BLOCKED_STREAMS</a>.
     */
    @Deprecated
    long HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS = Http3Settings.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS;

    /**
     * @deprecated Use {@link Http3Settings#HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL} instead.
     * See <a href="https://www.rfc-editor.org/rfc/rfc9220.html#section-5">
     * SETTINGS_ENABLE_CONNECT_PROTOCOL</a>.
     */
    @Deprecated
    long HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL = Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL;

    /**
     * @deprecated Use {@link Http3Settings#HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE} instead.
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4.1">
     * SETTINGS_MAX_FIELD_SECTION_SIZE</a>.
     */
    @Deprecated
    long HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE = Http3Settings.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE;

    default Http3Settings settings() {
        throw new UnsupportedOperationException(
                "Http3SettingsFrame.settings() not implemented in this version");
    }

    @Override
    default long type() {
        return Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
    }

    /**
     * Get a setting from the frame.
     *
     * @param key   the key of the setting.
     * @return      the value of the setting or {@code null} if none was found with the given key.
     * @deprecated Use typed accessors via {@link #settings()} instead.
     * For example, {@code frame.settings().connectProtocolEnabled()}.
     */
    @Deprecated
    @Nullable
    default Long get(long key){
        return settings().get(key);
    }

    /**
     * Get a setting from the frame.
     *
     * @param key   the key of the setting.
     * @param defaultValue If the setting does not exist.
     * @return the value of the setting or {@code defaultValue} if none was found with the given key.
     * @deprecated Use typed accessors via {@link #settings()} instead.
     * * For example, {@code frame.settings().qpackBlockedStreams()}.
     */
    @Deprecated
    default Long getOrDefault(long key, long defaultValue) {
        final Long val = get(key);
        return val == null ? defaultValue : val;
    }

    /**
     * Put a setting in the frame.
     *
     * @param key       the key of the setting
     * @param value     the value of the setting.
     * @return          the previous stored valued for the given key or {@code null} if none was stored before.
     * @deprecated Use typed accessors via {@link #settings()} instead.
     * * For example, {@code frame.settings().enableConnectProtocol(true)}.
     */
    @Nullable
    default Long put(long key, Long value){
        return settings().put(key, value);
    }
}
