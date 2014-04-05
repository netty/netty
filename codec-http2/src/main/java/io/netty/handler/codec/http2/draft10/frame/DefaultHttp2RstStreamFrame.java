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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.MAX_UNSIGNED_INT;

/**
 * Default implementation of {@link Http2RstStreamFrame}.
 */
public final class DefaultHttp2RstStreamFrame implements Http2RstStreamFrame {
    private final int streamId;
    private final long errorCode;

    private DefaultHttp2RstStreamFrame(Builder builder) {
        streamId = builder.streamId;
        errorCode = builder.errorCode;
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public long getErrorCode() {
        return errorCode;
    }

    @Override
    public boolean isEndOfStream() {
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (errorCode ^ (errorCode >>> 32));
        result = prime * result + streamId;
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
        DefaultHttp2RstStreamFrame other = (DefaultHttp2RstStreamFrame) obj;
        if (errorCode != other.errorCode) {
            return false;
        }
        if (streamId != other.streamId) {
            return false;
        }
        return true;
    }

    /**
     * Builds instances of {@link DefaultHttp2RstStreamFrame}.
     */
    public static class Builder {
        private int streamId;
        private long errorCode = -1L;

        public Builder setStreamId(int streamId) {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be > 0.");
            }
            this.streamId = streamId;
            return this;
        }

        public Builder setErrorCode(long errorCode) {
            if (errorCode < 0 || errorCode > MAX_UNSIGNED_INT) {
                throw new IllegalArgumentException("Invalid errorCode value.");
            }
            this.errorCode = errorCode;
            return this;
        }

        public DefaultHttp2RstStreamFrame build() {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be set.");
            }
            if (errorCode < 0L) {
                throw new IllegalArgumentException("ErrorCode must be set.");
            }
            return new DefaultHttp2RstStreamFrame(this);
        }
    }
}
