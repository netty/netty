
package io.netty.handler.codec.http3;

import io.netty.util.collection.LongObjectHashMap;

import javax.annotation.Nullable;

import static java.lang.Long.toHexString;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Settings for endpoint in an HTTP/3 connection.
 * Similar to {@link io.netty.handler.codec.http2.Http2Settings}, but uses long-based keys as per HTTP/3 spec.
 */
public final class Http3Settings extends LongObjectHashMap<Long> {

    private static final Long TRUE = 1L;
    private static final Long FALSE = 0L;

    public Http3Settings() {
        super();
    }

    public Http3Settings(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public Http3Settings(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    @Nullable
    public Long put(long key, Long value) {
        verifyStandardSetting(key, value);
        return super.put(key, value);
    }

    // --- Standard HTTP/3 setting constants ---
    public static final long HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY = 0x1;
    public static final long HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE   = 0x6;
    public static final long HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS    = 0x7;
    public static final long HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL  = 0x8;

    // --- Typed accessors ---

    /** SETTINGS_QPACK_MAX_TABLE_CAPACITY */
    @Nullable
    public Long qpackMaxTableCapacity() {
        return get(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY);
    }

    public Http3Settings qpackMaxTableCapacity(long value) {
        put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, Long.valueOf(value));
        return this;
    }


    /** SETTINGS_MAX_FIELD_SECTION_SIZE */
    @Nullable
    public Long maxFieldSectionSize() {
        return get(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
    }

    public Http3Settings maxFieldSectionSize(long value) {
        put(HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, Long.valueOf(value));
        return this;
    }

    /** SETTINGS_QPACK_BLOCKED_STREAMS */
    @Nullable
    public Long qpackBlockedStreams() {
        return get(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS);
    }

    public Http3Settings qpackBlockedStreams(long value) {
        put(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, Long.valueOf(value));
        return this;
    }

    /** SETTINGS_ENABLE_CONNECT_PROTOCOL (RFC 9220) */
    @Nullable
    public Boolean connectProtocolEnabled() {
        Long value = get(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL);
        if (value == null) {
            return null;
        }
        return TRUE.equals(value);
    }

    public Http3Settings enableConnectProtocol(boolean enabled) {
        put(HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, enabled ? TRUE : FALSE);
        return this;
    }

    // --- Helper methods ---

    public Http3Settings copyFrom(Http3Settings http3Settings) {
        checkNotNull(http3Settings, "http3Settings");
        clear();
        putAll(http3Settings);
        return this;
    }

    /**
     * Verify validity of known standard HTTP/3 settings.
     */
    private static void verifyStandardSetting(long key, Long value) {
        checkNotNull(value, "value");

        switch ((int) key) {
            case (int) HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
                if (value < 0) {
                    throw new IllegalArgumentException("QPACK_MAX_TABLE_CAPACITY invalid: " + value + " (must be >= 0)");
                }
                break;
            case (int) HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
                if (value < 0) {
                    throw new IllegalArgumentException("QPACK_BLOCKED_STREAMS invalid: " + value + " (must be >= 0)");
                }
                break;
            case (int) HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
                if (value < 0) {
                    throw new IllegalArgumentException("MAX_FIELD_SECTION_SIZE invalid: " + value + " (must be >= 0)");
                }
                break;
            case (int) HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL:
                if (value != 0L && value != 1L) {
                    throw new IllegalArgumentException("ENABLE_CONNECT_PROTOCOL invalid: " + value + " (expected 0 or 1)");
                }
                break;
            default:
                // Allow unknown custom settings, but require non-negative 64-bit values
                if (value < 0) {
                    throw new IllegalArgumentException("Non-standard setting 0x" + toHexString(key) + " invalid: " + value);
                }
                break;
        }
    }

    @Override
    protected String keyToString(long key) {
        switch ((int) key) {
            case (int) HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY:
                return "QPACK_MAX_TABLE_CAPACITY";
            case (int) HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS:
                return "QPACK_BLOCKED_STREAMS";
            case (int) HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE:
                return "MAX_FIELD_SECTION_SIZE";
            case (int) HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL:
                return "ENABLE_CONNECT_PROTOCOL";
            default:
                return "0x" + toHexString(key);
        }
    }
    /**
     * Returns a default HTTP/3 settings object:
     * <ul>
     *   <li>QPACK_MAX_TABLE_CAPACITY = 0</li>
     *   <li>QPACK_BLOCKED_STREAMS = 0</li>
     *   <li>ENABLE_CONNECT_PROTOCOL = false</li>
     *   <li>MAX_FIELD_SECTION_SIZE = unlimited (not set)</li>
     * </ul>
     */

    public static Http3Settings defaultSettings() {
        return new Http3Settings()
                .qpackMaxTableCapacity(0)
                .qpackBlockedStreams(0)
                .enableConnectProtocol(false);
    }
}
