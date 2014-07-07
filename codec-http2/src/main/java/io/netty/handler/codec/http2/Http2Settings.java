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
import io.netty.util.collection.IntObjectHashMap;

/**
 * Settings for one endpoint in an HTTP/2 connection. Each of the values are optional as defined in
 * the spec for the SETTINGS frame. Permits storage of arbitrary key/value pairs but provides helper
 * methods for standard settings.
 */
public final class Http2Settings extends IntObjectHashMap<Long> {

    public Http2Settings() {
    }

    public Http2Settings(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public Http2Settings(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Long put(int key, Long value) {
        verifyStandardSetting(key, value);
        return super.put(key, value);
    }

    public Long headerTableSize() {
        return get(SETTINGS_HEADER_TABLE_SIZE);
    }

    public Http2Settings headerTableSize(long value) {
        put(SETTINGS_HEADER_TABLE_SIZE, value);
        return this;
    }

    public Boolean pushEnabled() {
        Long value = get(SETTINGS_ENABLE_PUSH);
        if (value == null) {
            return null;
        }
        return value != 0L;
    }

    public Http2Settings pushEnabled(boolean enabled) {
        put(SETTINGS_ENABLE_PUSH, enabled? 1L : 0L);
        return this;
    }

    public Long maxConcurrentStreams() {
        return get(SETTINGS_MAX_CONCURRENT_STREAMS);
    }

    public Http2Settings maxConcurrentStreams(long value) {
        put(SETTINGS_MAX_CONCURRENT_STREAMS, value);
        return this;
    }

    public Integer initialWindowSize() {
        Long value = get(SETTINGS_INITIAL_WINDOW_SIZE);
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    public Http2Settings initialWindowSize(int value) {
        put(SETTINGS_INITIAL_WINDOW_SIZE, (long) value);
        return this;
    }

    public Http2Settings copyFrom(Http2Settings settings) {
        clear();
        putAll(settings);
        return this;
    }

    private void verifyStandardSetting(int key, Long value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        switch (key) {
            case SETTINGS_HEADER_TABLE_SIZE:
                if (value < 0L || value > MAX_UNSIGNED_INT) {
                    throw new IllegalArgumentException("Setting HEADER_TABLE_SIZE is invalid: " + value);
                }
                break;
            case SETTINGS_ENABLE_PUSH:
                if (value != 0L && value != 1L) {
                    throw new IllegalArgumentException("Setting ENABLE_PUSH is invalid: " + value);
                }
                break;
            case SETTINGS_MAX_CONCURRENT_STREAMS:
                if (value < 0L || value > MAX_UNSIGNED_INT) {
                    throw new IllegalArgumentException("Setting MAX_CONCURRENT_STREAMS is invalid: " + value);
                }
                break;
            case SETTINGS_INITIAL_WINDOW_SIZE:
                if (value < 0L || value > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Setting INITIAL_WINDOW_SIZE is invalid: " + value);
                }
                break;
        }
    }
}
