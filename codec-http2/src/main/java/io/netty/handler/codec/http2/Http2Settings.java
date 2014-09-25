/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_ENABLE_PUSH;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.isMaxFrameSizeValid;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.collection.IntObjectHashMap;

/**
 * Settings for one endpoint in an HTTP/2 connection. Each of the values are optional as defined in
 * the spec for the SETTINGS frame. Permits storage of arbitrary key/value pairs but provides helper
 * methods for standard settings.
 */
public final class Http2Settings extends IntObjectHashMap<Long> {

    public Http2Settings() {
        this(6 /* number of standard settings */);
    }

    public Http2Settings(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public Http2Settings(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Overrides the superclass method to perform verification of standard HTTP/2 settings.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    @Override
    public Long put(int key, Long value) {
        verifyStandardSetting(key, value);
        return super.put(key, value);
    }

    /**
     * Gets the {@code SETTINGS_HEADER_TABLE_SIZE} value. If unavailable, returns {@code null}.
     */
    public Long headerTableSize() {
        return get(SETTINGS_HEADER_TABLE_SIZE);
    }

    /**
     * Sets the {@code SETTINGS_HEADER_TABLE_SIZE} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings headerTableSize(long value) {
        put(SETTINGS_HEADER_TABLE_SIZE, value);
        return this;
    }

    /**
     * Gets the {@code SETTINGS_ENABLE_PUSH} value. If unavailable, returns {@code null}.
     */
    public Boolean pushEnabled() {
        Long value = get(SETTINGS_ENABLE_PUSH);
        if (value == null) {
            return null;
        }
        return value != 0L;
    }

    /**
     * Sets the {@code SETTINGS_ENABLE_PUSH} value.
     */
    public Http2Settings pushEnabled(boolean enabled) {
        put(SETTINGS_ENABLE_PUSH, enabled ? 1L : 0L);
        return this;
    }

    /**
     * Gets the {@code SETTINGS_MAX_CONCURRENT_STREAMS} value. If unavailable, returns {@code null}.
     */
    public Long maxConcurrentStreams() {
        return get(SETTINGS_MAX_CONCURRENT_STREAMS);
    }

    /**
     * Sets the {@code SETTINGS_MAX_CONCURRENT_STREAMS} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings maxConcurrentStreams(long value) {
        put(SETTINGS_MAX_CONCURRENT_STREAMS, value);
        return this;
    }

    /**
     * Gets the {@code SETTINGS_INITIAL_WINDOW_SIZE} value. If unavailable, returns {@code null}.
     */
    public Integer initialWindowSize() {
        return getIntValue(SETTINGS_INITIAL_WINDOW_SIZE);
    }

    /**
     * Sets the {@code SETTINGS_INITIAL_WINDOW_SIZE} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings initialWindowSize(int value) {
        put(SETTINGS_INITIAL_WINDOW_SIZE, (long) value);
        return this;
    }

    /**
     * Gets the {@code SETTINGS_MAX_FRAME_SIZE} value. If unavailable, returns {@code null}.
     */
    public Integer maxFrameSize() {
        return getIntValue(SETTINGS_MAX_FRAME_SIZE);
    }

    /**
     * Sets the {@code SETTINGS_MAX_FRAME_SIZE} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings maxFrameSize(int value) {
        put(SETTINGS_MAX_FRAME_SIZE, (long) value);
        return this;
    }

    /**
     * Gets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value. If unavailable, returns {@code null}.
     */
    public Integer maxHeaderListSize() {
        return getIntValue(SETTINGS_MAX_HEADER_LIST_SIZE);
    }

    /**
     * Sets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings maxHeaderListSize(int value) {
        put(SETTINGS_MAX_HEADER_LIST_SIZE, (long) value);
        return this;
    }

    /**
     * Clears and then copies the given settings into this object.
     */
    public Http2Settings copyFrom(Http2Settings settings) {
        clear();
        putAll(settings);
        return this;
    }

    Integer getIntValue(int key) {
        Long value = get(key);
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private void verifyStandardSetting(int key, Long value) {
        checkNotNull(value, "value");
        switch (key) {
            case SETTINGS_HEADER_TABLE_SIZE:
                if (value < 0L || value > MAX_UNSIGNED_INT) {
                    throw new IllegalArgumentException("Setting HEADER_TABLE_SIZE is invalid: "
                            + value);
                }
                break;
            case SETTINGS_ENABLE_PUSH:
                if (value != 0L && value != 1L) {
                    throw new IllegalArgumentException("Setting ENABLE_PUSH is invalid: " + value);
                }
                break;
            case SETTINGS_MAX_CONCURRENT_STREAMS:
                if (value < 0L || value > MAX_UNSIGNED_INT) {
                    throw new IllegalArgumentException(
                            "Setting MAX_CONCURRENT_STREAMS is invalid: " + value);
                }
                break;
            case SETTINGS_INITIAL_WINDOW_SIZE:
                if (value < 0L || value > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Setting INITIAL_WINDOW_SIZE is invalid: "
                            + value);
                }
                break;
            case SETTINGS_MAX_FRAME_SIZE:
                if (!isMaxFrameSizeValid(value.intValue())) {
                    throw new IllegalArgumentException("Setting MAX_FRAME_SIZE is invalid: "
                            + value);
                }
                break;
            case SETTINGS_MAX_HEADER_LIST_SIZE:
                if (value < 0) {
                    throw new IllegalArgumentException("Setting MAX_HEADER_LIST_SIZE is invalid: "
                            + value);
                }
                break;
        }
    }

    @Override
    protected String keyToString(int key) {
        switch (key) {
            case SETTINGS_HEADER_TABLE_SIZE:
                return "HEADER_TABLE_SIZE";
            case SETTINGS_ENABLE_PUSH:
                return "ENABLE_PUSH";
            case SETTINGS_MAX_CONCURRENT_STREAMS:
                return "MAX_CONCURRENT_STREAMS";
            case SETTINGS_INITIAL_WINDOW_SIZE:
                return "INITIAL_WINDOW_SIZE";
            case SETTINGS_MAX_FRAME_SIZE:
                return "MAX_FRAME_SIZE";
            case SETTINGS_MAX_HEADER_LIST_SIZE:
                return "MAX_HEADER_LIST_SIZE";
            default:
                // Unknown keys.
                return super.keyToString(key);
        }
    }
}
