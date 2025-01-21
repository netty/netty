/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.util.collection.CharObjectHashMap;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.NUM_STANDARD_SETTINGS;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_ENABLE_PUSH;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_CONCURRENT_STREAMS;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.isMaxFrameSizeValid;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Integer.toHexString;

/**
 * Settings for one endpoint in an HTTP/2 connection. Each of the values are optional as defined in
 * the spec for the SETTINGS frame. Permits storage of arbitrary key/value pairs but provides helper
 * methods for standard settings.
 */
public final class Http2Settings extends CharObjectHashMap<Long> {
    /**
     * Default capacity based on the number of standard settings from the HTTP/2 spec, adjusted so that adding all of
     * the standard settings will not cause the map capacity to change.
     */
    private static final int DEFAULT_CAPACITY = (int) (NUM_STANDARD_SETTINGS / DEFAULT_LOAD_FACTOR) + 1;
    private static final Long FALSE = 0L;
    private static final Long TRUE = 1L;

    public Http2Settings() {
        this(DEFAULT_CAPACITY);
    }

    public Http2Settings(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public Http2Settings(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Adds the given setting key/value pair. For standard settings defined by the HTTP/2 spec, performs
     * validation on the values.
     *
     * @throws IllegalArgumentException if verification for a standard HTTP/2 setting fails.
     */
    @Override
    public Long put(char key, Long value) {
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
        put(SETTINGS_HEADER_TABLE_SIZE, Long.valueOf(value));
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
        return TRUE.equals(value);
    }

    /**
     * Sets the {@code SETTINGS_ENABLE_PUSH} value.
     */
    public Http2Settings pushEnabled(boolean enabled) {
        put(SETTINGS_ENABLE_PUSH, enabled ? TRUE : FALSE);
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
        put(SETTINGS_MAX_CONCURRENT_STREAMS, Long.valueOf(value));
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
        put(SETTINGS_INITIAL_WINDOW_SIZE, Long.valueOf(value));
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
        put(SETTINGS_MAX_FRAME_SIZE, Long.valueOf(value));
        return this;
    }

    /**
     * Gets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value. If unavailable, returns {@code null}.
     */
    public Long maxHeaderListSize() {
        return get(SETTINGS_MAX_HEADER_LIST_SIZE);
    }

    /**
     * Sets the {@code SETTINGS_MAX_HEADER_LIST_SIZE} value.
     *
     * @throws IllegalArgumentException if verification of the setting fails.
     */
    public Http2Settings maxHeaderListSize(long value) {
        put(SETTINGS_MAX_HEADER_LIST_SIZE, Long.valueOf(value));
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

    /**
     * A helper method that returns {@link Long#intValue()} on the return of {@link #get(char)}, if present. Note that
     * if the range of the value exceeds {@link Integer#MAX_VALUE}, the {@link #get(char)} method should
     * be used instead to avoid truncation of the value.
     */
    public Integer getIntValue(char key) {
        Long value = get(key);
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private static void verifyStandardSetting(int key, Long value) {
        checkNotNull(value, "value");
        switch (key) {
            case SETTINGS_HEADER_TABLE_SIZE:
                if (value < MIN_HEADER_TABLE_SIZE || value > MAX_HEADER_TABLE_SIZE) {
                    throw new IllegalArgumentException("Setting HEADER_TABLE_SIZE is invalid: " + value +
                            ", expected [" + MIN_HEADER_TABLE_SIZE + ", " + MAX_HEADER_TABLE_SIZE + ']');
                }
                break;
            case SETTINGS_ENABLE_PUSH:
                if (value != 0L && value != 1L) {
                    throw new IllegalArgumentException("Setting ENABLE_PUSH is invalid: " + value +
                            ", expected [0, 1]");
                }
                break;
            case SETTINGS_MAX_CONCURRENT_STREAMS:
                if (value < MIN_CONCURRENT_STREAMS || value > MAX_CONCURRENT_STREAMS) {
                    throw new IllegalArgumentException("Setting MAX_CONCURRENT_STREAMS is invalid: " + value +
                            ", expected [" + MIN_CONCURRENT_STREAMS + ", " + MAX_CONCURRENT_STREAMS + ']');
                }
                break;
            case SETTINGS_INITIAL_WINDOW_SIZE:
                if (value < MIN_INITIAL_WINDOW_SIZE || value > MAX_INITIAL_WINDOW_SIZE) {
                    throw new IllegalArgumentException("Setting INITIAL_WINDOW_SIZE is invalid: " + value +
                            ", expected [" + MIN_INITIAL_WINDOW_SIZE + ", " + MAX_INITIAL_WINDOW_SIZE + ']');
                }
                break;
            case SETTINGS_MAX_FRAME_SIZE:
                if (!isMaxFrameSizeValid(value.intValue())) {
                    throw new IllegalArgumentException("Setting MAX_FRAME_SIZE is invalid: " + value +
                            ", expected [" + MAX_FRAME_SIZE_LOWER_BOUND + ", " + MAX_FRAME_SIZE_UPPER_BOUND + ']');
                }
                break;
            case SETTINGS_MAX_HEADER_LIST_SIZE:
                if (value < MIN_HEADER_LIST_SIZE || value > MAX_HEADER_LIST_SIZE) {
                    throw new IllegalArgumentException("Setting MAX_HEADER_LIST_SIZE is invalid: " + value +
                            ", expected [" + MIN_HEADER_LIST_SIZE + ", " + MAX_HEADER_LIST_SIZE + ']');
                }
                break;
            default:
                // Non-standard HTTP/2 setting
                if (value < 0 || value > MAX_UNSIGNED_INT) {
                    throw new IllegalArgumentException("Non-standard setting 0x" + toHexString(key) + " is invalid: " +
                            value + ", expected unsigned 32-bit value");
                }
                break;
        }
    }

    @Override
    protected String keyToString(char key) {
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
                return "0x" + toHexString(key);
        }
    }

    public static Http2Settings defaultSettings() {
        return new Http2Settings().maxHeaderListSize(DEFAULT_HEADER_LIST_SIZE);
    }
}
