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

package io.netty.handler.codec.http2.draft10.frame;

/**
 * Default implementation of {@link Http2SettingsFrame}.
 */
public final class DefaultHttp2SettingsFrame implements Http2SettingsFrame {

    private final boolean ack;
    private final Integer headerTableSize;
    private final Boolean pushEnabled;
    private final Long maxConcurrentStreams;
    private final Integer initialWindowSize;

    private DefaultHttp2SettingsFrame(Builder builder) {
        ack = builder.ack;
        headerTableSize = builder.headerTableSize;
        pushEnabled = builder.pushEnabled;
        maxConcurrentStreams = builder.maxConcurrentStreams;
        initialWindowSize = builder.initialWindowSize;
    }

    @Override
    public boolean isAck() {
        return ack;
    }

    @Override
    public Integer getHeaderTableSize() {
        return headerTableSize;
    }

    @Override
    public Boolean getPushEnabled() {
        return pushEnabled;
    }

    @Override
    public Long getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    @Override
    public Integer getInitialWindowSize() {
        return initialWindowSize;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (ack ? 1231 : 1237);
        result = prime * result + ((headerTableSize == null) ? 0 : headerTableSize.hashCode());
        result = prime * result + ((initialWindowSize == null) ? 0 : initialWindowSize.hashCode());
        result =
                prime * result + ((maxConcurrentStreams == null) ? 0 : maxConcurrentStreams.hashCode());
        result = prime * result + ((pushEnabled == null) ? 0 : pushEnabled.hashCode());
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
        DefaultHttp2SettingsFrame other = (DefaultHttp2SettingsFrame) obj;
        if (ack != other.ack) {
            return false;
        }
        if (headerTableSize == null) {
            if (other.headerTableSize != null) {
                return false;
            }
        } else if (!headerTableSize.equals(other.headerTableSize)) {
            return false;
        }
        if (initialWindowSize == null) {
            if (other.initialWindowSize != null) {
                return false;
            }
        } else if (!initialWindowSize.equals(other.initialWindowSize)) {
            return false;
        }
        if (maxConcurrentStreams == null) {
            if (other.maxConcurrentStreams != null) {
                return false;
            }
        } else if (!maxConcurrentStreams.equals(other.maxConcurrentStreams)) {
            return false;
        }
        if (pushEnabled == null) {
            if (other.pushEnabled != null) {
                return false;
            }
        } else if (!pushEnabled.equals(other.pushEnabled)) {
            return false;
        }
        return true;
    }

    /**
     * Builds instances of {@link DefaultHttp2SettingsFrame}.
     */
    public static class Builder {
        private boolean ack;
        private Integer headerTableSize;
        private Boolean pushEnabled;
        private Long maxConcurrentStreams;
        private Integer initialWindowSize;

        public Builder setAck(boolean ack) {
            this.ack = ack;
            return this;
        }

        public Builder setHeaderTableSize(int headerTableSize) {
            this.headerTableSize = headerTableSize;
            return this;
        }

        public Builder setPushEnabled(boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
            return this;
        }

        public Builder setMaxConcurrentStreams(long maxConcurrentStreams) {
            this.maxConcurrentStreams = maxConcurrentStreams;
            return this;
        }

        public Builder setInitialWindowSize(int initialWindowSize) {
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        public DefaultHttp2SettingsFrame build() {
            if (ack && (headerTableSize != null || pushEnabled != null || maxConcurrentStreams != null
                    || initialWindowSize != null)) {
                throw new IllegalArgumentException("Ack frame must not contain settings");
            }
            return new DefaultHttp2SettingsFrame(this);
        }
    }
}
