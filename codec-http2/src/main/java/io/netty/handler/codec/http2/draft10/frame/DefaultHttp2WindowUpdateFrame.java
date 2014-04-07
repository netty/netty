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
 * Default implementation of {@link Http2WindowUpdateFrame}.
 */
public final class DefaultHttp2WindowUpdateFrame implements Http2WindowUpdateFrame {

    private final int streamId;
    private final int windowSizeIncrement;

    private DefaultHttp2WindowUpdateFrame(Builder builder) {
        streamId = builder.streamId;
        windowSizeIncrement = builder.windowSizeIncrement;
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public boolean isEndOfStream() {
        return false;
    }

    @Override
    public int getWindowSizeIncrement() {
        return windowSizeIncrement;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + streamId;
        result = prime * result + windowSizeIncrement;
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
        DefaultHttp2WindowUpdateFrame other = (DefaultHttp2WindowUpdateFrame) obj;
        if (streamId != other.streamId) {
            return false;
        }
        if (windowSizeIncrement != other.windowSizeIncrement) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append("[");
        builder.append("streamId=").append(streamId);
        builder.append(", windowSizeIncrement=").append(windowSizeIncrement);
        builder.append("]");
        return builder.toString();
    }

    /**
     * Builds instances of {@link DefaultHttp2WindowUpdateFrame}.
     */
    public static class Builder {
        private int streamId;
        private int windowSizeIncrement;

        public Builder setStreamId(int streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder setWindowSizeIncrement(int windowSizeIncrement) {
            this.windowSizeIncrement = windowSizeIncrement;
            return this;
        }

        public DefaultHttp2WindowUpdateFrame build() {
            if (streamId < 0) {
                throw new IllegalArgumentException("StreamId must be >= 0.");
            }
            if (windowSizeIncrement < 0) {
                throw new IllegalArgumentException("SindowSizeIncrement must be >= 0.");
            }
            return new DefaultHttp2WindowUpdateFrame(this);
        }
    }
}
