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

import java.util.NoSuchElementException;

/**
 * Settings for one endpoint in an HTTP/2 connection. Each of the values are optional as defined in
 * the spec for the SETTINGS frame.
 */
public class Http2Settings {
    private static final byte MAX_HEADER_TABLE_SIZE_MASK = 0x1;
    private static final byte PUSH_ENABLED_MASK = 0x2;
    private static final byte MAX_CONCURRENT_STREAMS_MASK = 0x4;
    private static final byte INITIAL_WINDOW_SIZE_MASK = 0x8;
    private static final byte ALLOW_COMPRESSION_MASK = 0x10;

    private byte enabled;
    private int maxHeaderTableSize;
    private boolean pushEnabled;
    private int maxConcurrentStreams;
    private int initialWindowSize;
    private boolean allowCompressedData;

    /**
     * Indicates whether or not the headerTableSize value is available.
     */
    public boolean hasMaxHeaderTableSize() {
        return isEnabled(MAX_HEADER_TABLE_SIZE_MASK);
    }

    /**
     * Gets the maximum HPACK header table size or throws {@link NoSuchElementException} if the
     * value has not been set.
     */
    public int maxHeaderTableSize() {
        if (!hasMaxHeaderTableSize()) {
            throw new NoSuchElementException("headerTableSize");
        }
        return maxHeaderTableSize;
    }

    /**
     * Sets the maximum HPACK header table size to the specified value.
     */
    public Http2Settings maxHeaderTableSize(int headerTableSize) {
        if (headerTableSize < 0) {
            throw new IllegalArgumentException("headerTableSize must be >= 0");
        }

        enable(MAX_HEADER_TABLE_SIZE_MASK);
        maxHeaderTableSize = headerTableSize;
        return this;
    }

    /**
     * Indicates whether or not the pushEnabled value is available.
     */
    public boolean hasPushEnabled() {
        return isEnabled(PUSH_ENABLED_MASK);
    }

    /**
     * Gets whether or not server push is enabled or throws {@link NoSuchElementException} if the
     * value has not been set.
     */
    public boolean pushEnabled() {
        if (!hasPushEnabled()) {
            throw new NoSuchElementException("pushEnabled");
        }
        return pushEnabled;
    }

    /**
     * Sets whether or not server push is enabled.
     */
    public Http2Settings pushEnabled(boolean pushEnabled) {
        enable(PUSH_ENABLED_MASK);
        this.pushEnabled = pushEnabled;
        return this;
    }

    /**
     * Indicates whether or not the maxConcurrentStreams value is available.
     */
    public boolean hasMaxConcurrentStreams() {
        return isEnabled(MAX_CONCURRENT_STREAMS_MASK);
    }

    /**
     * Gets the maximum allowed concurrent streams or throws {@link NoSuchElementException} if the
     * value has not been set.
     */
    public int maxConcurrentStreams() {
        if (!hasMaxConcurrentStreams()) {
            throw new NoSuchElementException("maxConcurrentStreams");
        }
        return maxConcurrentStreams;
    }

    /**
     * Sets the maximum allowed concurrent streams to the specified value.
     */
    public Http2Settings maxConcurrentStreams(int maxConcurrentStreams) {
        if (maxConcurrentStreams < 0) {
            throw new IllegalArgumentException("maxConcurrentStreams must be >= 0");
        }
        enable(MAX_CONCURRENT_STREAMS_MASK);
        this.maxConcurrentStreams = maxConcurrentStreams;
        return this;
    }

    /**
     * Indicates whether or not the initialWindowSize value is available.
     */
    public boolean hasInitialWindowSize() {
        return isEnabled(INITIAL_WINDOW_SIZE_MASK);
    }

    /**
     * Gets the initial flow control window size or throws {@link NoSuchElementException} if the
     * value has not been set.
     */
    public int initialWindowSize() {
        if (!hasInitialWindowSize()) {
            throw new NoSuchElementException("initialWindowSize");
        }
        return initialWindowSize;
    }

    /**
     * Sets the initial flow control window size to the specified value.
     */
    public Http2Settings initialWindowSize(int initialWindowSize) {
        if (initialWindowSize < 0) {
            throw new IllegalArgumentException("initialWindowSize must be >= 0");
        }
        enable(INITIAL_WINDOW_SIZE_MASK);
        this.initialWindowSize = initialWindowSize;
        return this;
    }

    /**
     * Indicates whether or not the allowCompressedData value is available.
     */
    public boolean hasAllowCompressedData() {
        return isEnabled(ALLOW_COMPRESSION_MASK);
    }

    /**
     * Gets whether the endpoint allows compressed data or throws {@link NoSuchElementException} if
     * the value has not been set.
     */
    public boolean allowCompressedData() {
        if (!hasAllowCompressedData()) {
            throw new NoSuchElementException("allowCompressedData");
        }
        return allowCompressedData;
    }

    /**
     * Sets whether or not the endpoing allows compressed data.
     */
    public Http2Settings allowCompressedData(boolean allowCompressedData) {
        enable(ALLOW_COMPRESSION_MASK);
        this.allowCompressedData = allowCompressedData;
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (allowCompressedData ? 1231 : 1237);
        result = prime * result + enabled;
        result = prime * result + maxHeaderTableSize;
        result = prime * result + initialWindowSize;
        result = prime * result + maxConcurrentStreams;
        result = prime * result + (pushEnabled ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Http2Settings other = (Http2Settings) obj;
        if (allowCompressedData != other.allowCompressedData) {
            return false;
        }
        if (enabled != other.enabled) {
            return false;
        }
        if (maxHeaderTableSize != other.maxHeaderTableSize) {
            return false;
        }
        if (initialWindowSize != other.initialWindowSize) {
            return false;
        }
        if (maxConcurrentStreams != other.maxConcurrentStreams) {
            return false;
        }
        if (pushEnabled != other.pushEnabled) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Http2Settings [");
        if (hasMaxHeaderTableSize()) {
            builder.append("maxHeaderTableSize=").append(maxHeaderTableSize).append(",");
        }
        if (hasPushEnabled()) {
            builder.append("pushEnabled=").append(pushEnabled).append(",");
        }
        if (hasMaxConcurrentStreams()) {
            builder.append("maxConcurrentStreams=").append(maxConcurrentStreams).append(",");
        }
        if (hasInitialWindowSize()) {
            builder.append("initialWindowSize=").append(initialWindowSize).append(",");
        }
        if (hasAllowCompressedData()) {
            builder.append("allowCompressedData=").append(allowCompressedData).append(",");
        }
        builder.append("]");
        return builder.toString();
    }

    private void enable(int mask) {
        enabled |= mask;
    }

    private boolean isEnabled(int mask) {
        return (enabled & mask) > 0;
    }
}
