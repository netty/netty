/*
 * Copyright 2025 The Netty Project
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

/**
 * Represents a collection of HTTP/3 settings as defined by the
 * <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4">
 * HTTP/3 specification</a>.
 *
 * <p>This class provides type-safe accessors for standard HTTP/3 settings such as:
 * <ul>
 *   <li>{@code QPACK_MAX_TABLE_CAPACITY} (0x1)</li>
 *   <li>{@code MAX_FIELD_SECTION_SIZE} (0x6)</li>
 *   <li>{@code QPACK_BLOCKED_STREAMS} (0x7)</li>
 *   <li>{@code ENABLE_CONNECT_PROTOCOL} (0x8)</li>
 * </ul>
 *
 * <p>It is backed by a {@link LongObjectMap}.
 * Non-standard settings are permitted as long as they use positive values.
 * Reserved HTTP/2 setting identifiers are rejected.
 *
 */
public final class Http3Settings implements Iterable<Map.Entry<Long, Long>> {

    private final LongObjectMap<Long> settings;

    /**
     * QPACK maximum table capacity setting identifier (<b>0x1</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (QPACK-MAX_TABLE_CAPACITY)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Controls the maximum size of the dynamic table used by QPACK.
     */
    public static final long HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1;

    /**
     * Maximum field section size setting identifier (<b>0x6</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * RFC 9114, Section 7.2.4.1 (SETTINGS_MAX_FIELD_SECTION_SIZE)</a> , also referenced
     * in the <a href="https://datatracker.ietf.org/doc/html/rfc9114#section-7.2.4.1">
     * HTTP/3 SETTINGS registry (RFC 9114, Section 7.2.4.1)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Specifies the upper bound on the total size of HTTP field sections accepted by a peer.
     */
    public static final long HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE = 0x6;

    /**
     * QPACK blocked streams setting identifier (<b>0x7</b>).
     * <p>
     * Defined in <a href="https://datatracker.ietf.org/doc/html/rfc9204#section-5">
     * RFC 9204, Section 5 (QPACK_BLOCKED_STREAMS)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Indicates the maximum number of streams that can be blocked waiting for QPACK instructions.
     */
    public static final long HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS = 0x7;

    /**
     * ENABLE_CONNECT_PROTOCOL setting identifier (<b>0x8</b>).
     * <p>
     * Defined and registered in <a href="https://datatracker.ietf.org/doc/html/rfc9220#section-5">
     * RFC 9220, Section 5 (IANA Considerations)</a> and registered in
     * the <a href="https://www.iana.org/assignments/http3-parameters/http3-parameters.xhtml#settings">
     * HTTP/3 SETTINGS registry (IANA)</a>.
     * <br>
     * Enables use of the CONNECT protocol in HTTP/3 when set to 1; disabled when 0.
     */
    public static final long HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x8;

    private static final Long TRUE = 1L;
    private static final Long FALSE = 0L;

    /**
     * Creates a new instance
     */
    public Http3Settings() {
        this.settings = new LongObjectHashMap<>(4);
    }

    /**
     * Creates a new instance with the specified initial capacity.
     *
     * @param initialCapacity initial capacity of the underlying map
     */
    Http3Settings(int initialCapacity) {
        this.settings = new LongObjectHashMap<>(initialCapacity);
    }

    /**
     * Creates a new instance with the specified initial capacity and load factor.
     *
     * @param initialCapacity initial capacity of the underlying map
     * @param loadFactor load factor for the underlying map
     */
    Http3Settings(int initialCapacity, float loadFactor) {
        this.settings = new LongObjectHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * Stores a setting value for the specified identifier.
     * <p>
     * The key and value are validated according to the HTTP/3 specification.
     * Reserved HTTP/2 setting identifiers and negative values are not allowed.
     *
     * @param key   the numeric setting identifier
     * @param value the setting value (non-null)
     * @return the previous value associated with the key, or {@code null} if none
     * @throws IllegalArgumentException if the key or value is invalid
     */
    @Nullable
    public Long put(long key, Long value) {
        verifyStandardSetting(key, value);
        return settings.put(key, value);
    }

    /**
     * Returns the value of the specified setting identifier.
     *
     * @param key the numeric setting identifier
     * @return the setting value, or {@code null} if not set
     */
    @Nullable
    public Long get(long key) {
        return settings.get(key);
    }

    /**
     * Returns the {@code QPACK_MAX_TABLE_CAPACITY} value.
     *
     * @return the current QPACK maximum table capacity, or {@code null} if not set
     */
    @Nullable
    public Long qpackMaxTableCapacity() {
        return get(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY);
    }

    /**
     * Sets the {@code QPACK_MAX_TABLE_CAPACITY} value.
     *
     * @param value QPACK maximum table capacity (must be ≥ 0)
     * @return this instance for method chaining
     */
    public Http3Settings qpackMaxTableCapacity(long value) {
        put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, value);
        return this;
    }

    /**
     * Returns the {@code MAX_FIELD_SECTION_SIZE} value.
     *
     * @return the maximum field section size, or {@code null} if not set
     */
    @Nullable
    public Long maxFieldSectionSize() {
        return get(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
    }

    /**
     * Sets the {@code MAX_FIELD_SECTION_SIZE} value.
     *
     * @param value maximum field section size (must be ≥ 0)
     * @return this instance for method chaining
     */
    public Http3Settings maxFieldSectionSize(long value) {
        put(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, value);
        return this;
    }

    /**
     * Returns the {@code QPACK_BLOCKED_STREAMS} value.
     *
     * @return the number of blocked streams, or {@code null} if not set
     */
    @Nullable
    public Long qpackBlockedStreams() {
        return get(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS);
    }

    /**
     * Sets the {@code QPACK_BLOCKED_STREAMS} value.
     *
     * @param value number of blocked streams (must be ≥ 0)
     * @return this instance for method chaining
     */
    public Http3Settings qpackBlockedStreams(long value) {
        put(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, value);
        return this;
    }

    /**
     * Returns whether the {@code ENABLE_CONNECT_PROTOCOL} setting is enabled.
     *
     * @return {@code true} if enabled, {@code false} if disabled, or {@code null} if not set
     */
    @Nullable
    public Boolean connectProtocolEnabled() {
        Long value = get(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL);
        return value == null ? null : TRUE.equals(value);
    }

    /**
     * Sets the {@code ENABLE_CONNECT_PROTOCOL} flag.
     *
     * @param enabled whether to enable the CONNECT protocol
     * @return this instance for method chaining
     */
    public Http3Settings enableConnectProtocol(boolean enabled) {
        put(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, enabled ? TRUE : FALSE);
        return this;
    }

    /**
     * Replaces all current settings with those from another {@link Http3Settings} instance.
     *
     * @param http3Settings the source settings (non-null)
     * @return this instance for method chaining
     */
    public Http3Settings putAll(Http3Settings http3Settings) {
        checkNotNull(http3Settings, "http3Settings");
        settings.putAll(http3Settings.settings);
        return this;
    }

    /**
     * Returns a new {@link Http3Settings} instance with default values:
     * <ul>
     *   <li>{@code QPACK_MAX_TABLE_CAPACITY} = 0</li>
     *   <li>{@code QPACK_BLOCKED_STREAMS} = 0</li>
     *   <li>{@code ENABLE_CONNECT_PROTOCOL} = false</li>
     *   <li>{@code MAX_FIELD_SECTION_SIZE} = unlimited</li>
     * </ul>
     *
     * @return a default {@link Http3Settings} instance
     */
    public static Http3Settings defaultSettings() {
        return new Http3Settings()
                .qpackMaxTableCapacity(0)
                .qpackBlockedStreams(0)
                .maxFieldSectionSize(Long.MAX_VALUE)
                .enableConnectProtocol(false);
    }

    /**
     * Returns an iterator over the settings entries in this object.
     * Each entry’s key is the numeric setting identifier, and the value is its numeric value.
     *
     * @return an iterator over immutable {@link Map.Entry} objects
     */
    @Override
    public Iterator<Map.Entry<Long, Long>> iterator() {
        Iterator<LongObjectMap.PrimitiveEntry<Long>> it = settings.entries().iterator();
        return new Iterator<Map.Entry<Long, Long>>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Map.Entry<Long, Long> next() {
                LongObjectMap.PrimitiveEntry<Long> entry = it.next();
                return new java.util.AbstractMap.SimpleImmutableEntry<>(entry.key(), entry.value());
            }
        };
    }

    /**
     * Compares this settings object to another for equality.
     * Two instances are equal if they contain the same key–value pairs.
     *
     * @param o the other object
     * @return {@code true} if equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Http3Settings)) {
            return false;
        }
        Http3Settings that = (Http3Settings) o;
        return settings.equals(that.settings);
    }

    /**
     * Returns the hash code of this settings object, based on its key–value pairs.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return settings.hashCode();
    }

    /**
     * Returns a string representation of this settings object in the form:
     * <pre>
     * Http3Settings{0x1=100, 0x6=16384, 0x7=0}
     * </pre>
     *
     * @return a human-readable string representation of the settings
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Http3Settings{");
        boolean first = true;
        for (LongObjectMap.PrimitiveEntry<Long> e : settings.entries()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("0x").append(toHexString(e.key())).append('=').append(e.value());
        }
        return sb.append('}').toString();
    }

    /**
     * Validates a setting key and value pair against HTTP/3 and HTTP/2 constraints.
     *
     * @param key the setting identifier
     * @param value the setting value
     * @throws IllegalArgumentException if the key or value violates the protocol specification
     */
    private static void verifyStandardSetting(long key, Long value) {
        checkNotNull(value, "value");
        if (Http3CodecUtils.isReservedHttp2Setting(key)) {
            throw new IllegalArgumentException("Setting is reserved for HTTP/2: " + key);
        }
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
                if (value < 0) {
                    throw new IllegalArgumentException("Setting 0x" + toHexString(key) + " invalid: " + value);
                }
        }
    }
}
