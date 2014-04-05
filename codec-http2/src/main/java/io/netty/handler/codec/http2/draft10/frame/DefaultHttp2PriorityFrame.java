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
 * Default implementation of {@link Http2PriorityFrame}.
 */
public final class DefaultHttp2PriorityFrame implements Http2PriorityFrame {

    private final int streamId;
    private final int priority;

    private DefaultHttp2PriorityFrame(Builder builder) {
        this.streamId = builder.streamId;
        this.priority = builder.priority;
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isEndOfStream() {
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + priority;
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
        DefaultHttp2PriorityFrame other = (DefaultHttp2PriorityFrame) obj;
        if (priority != other.priority) {
            return false;
        }
        if (streamId != other.streamId) {
            return false;
        }
        return true;
    }

    /**
     * Builds instances of {@link DefaultHttp2PriorityFrame}.
     */
    public static class Builder {
        private int streamId;
        private int priority = -1;

        public Builder setStreamId(int streamId) {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be > 0.");
            }
            this.streamId = streamId;
            return this;
        }

        public Builder setPriority(int priority) {
            if (priority < 0) {
                throw new IllegalArgumentException("Invalid priority.");
            }
            this.priority = priority;
            return this;
        }

        public DefaultHttp2PriorityFrame build() {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be set.");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("Priority must be set.");
            }
            return new DefaultHttp2PriorityFrame(this);
        }
    }
}
