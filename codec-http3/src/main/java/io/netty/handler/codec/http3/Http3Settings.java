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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http3;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

import static java.lang.Long.toHexString;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

public final class Http3Settings {

    private final LongObjectMap<Long> settings;

    // --- Standard HTTP/3 setting constants ---
    public static final long HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1;
    public static final long HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE   = 0x6;
    public static final long HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS    = 0x7;
    public static final long HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL  = 0x8;


    private static final Long TRUE = 1L;
    private static final Long FALSE = 0L;

    public Http3Settings() {
        this.settings = new LongObjectHashMap<>(4);
    }

    public Http3Settings(int initialCapacity) {
        this.settings = new LongObjectHashMap<>(initialCapacity);
    }

    public Http3Settings(int initialCapacity, float loadFactor) {
        this.settings = new LongObjectHashMap<>(initialCapacity, loadFactor);
    }

    @Nullable
    public Long put(long key, Long value) {
        verifyStandardSetting(key, value);
        return settings.put(key, value);
    }

    @Nullable
    public Long get(long key) {
        return settings.get(key);
    }




    @Nullable
    public Long qpackMaxTableCapacity() {
        return get(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY);
    }

    public Http3Settings qpackMaxTableCapacity(long value) {
        put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, value);
        return this;
    }

    @Nullable
    public Long maxFieldSectionSize() {
        return get(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
    }

    public Http3Settings maxFieldSectionSize(long value) {
        put(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, value);
        return this;
    }

    @Nullable
    public Long qpackBlockedStreams() {
        return get(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS);
    }

    public Http3Settings qpackBlockedStreams(long value) {
        put(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, value);
        return this;
    }

    @Nullable
    public Boolean connectProtocolEnabled() {
        Long value = get(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL);
        return value == null ? null : TRUE.equals(value);
    }

    public Http3Settings enableConnectProtocol(boolean enabled) {
        put(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, enabled ? TRUE : FALSE);
        return this;
    }

    public Http3Settings copyFrom(Http3Settings http3Settings) {
        checkNotNull(http3Settings, "http3Settings");
        settings.clear();
        settings.putAll(http3Settings.settings);
        return this;
    }

    private static void verifyStandardSetting(long key, Long value) {
        checkNotNull(value, "value");

        switch ((int) key) {
            case (int) HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
            case (int) HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
            case (int) HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
                if (value < 0) {
                    throw new IllegalArgumentException("Setting 0x" + toHexString(key)
                            + " invalid: " + value + " (must be >= 0)");
                }
                break;
            case (int) HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL:
                if (value != 0L && value != 1L) {
                    throw new IllegalArgumentException("ENABLE_CONNECT_PROTOCOL invalid: " + value
                            + " (expected 0 or 1)");
                }
                break;
            default:
                throw new IllegalArgumentException("Non-standard/not implemented setting 0x"
                        + toHexString(key) + " invalid: " + value);

        }
    }

    public static Http3Settings defaultSettings() {
        return new Http3Settings()
                .qpackMaxTableCapacity(0)
                .qpackBlockedStreams(0)
                .enableConnectProtocol(false);
    }


    public Iterator<Map.Entry<Long,Long>> iterator() {
        return settings.entrySet().iterator();
    }

    // --- Equality and Debugging ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Http3Settings)) return false;
        Http3Settings that = (Http3Settings) o;
        return settings.equals(that.settings);
    }

    @Override
    public int hashCode() {
        return settings.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Http3Settings{");
        boolean first = true;
        for (LongObjectMap.PrimitiveEntry<Long> e : settings.entries()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("0x").append(toHexString(e.key())).append('=').append(e.value());
        }
        return sb.append('}').toString();
    }
}
