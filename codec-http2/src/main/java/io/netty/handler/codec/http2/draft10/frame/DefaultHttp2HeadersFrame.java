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

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.DEFAULT_STREAM_PRIORITY;
import io.netty.handler.codec.http2.draft10.Http2Headers;

public final class DefaultHttp2HeadersFrame implements Http2HeadersFrame {

    private final int streamId;
    private final int priority;
    private final boolean endOfStream;
    private final Http2Headers headers;

    private DefaultHttp2HeadersFrame(Builder builder) {
        this.streamId = builder.streamId;
        this.priority = builder.priority;
        this.headers = builder.headersBuilder.build();
        this.endOfStream = builder.endOfStream;
    }

    @Override
    public int getStreamId() {
        return streamId;
    }

    @Override
    public boolean isEndOfStream() {
        return endOfStream;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public Http2Headers getHeaders() {
        return headers;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (endOfStream ? 1231 : 1237);
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
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
        DefaultHttp2HeadersFrame other = (DefaultHttp2HeadersFrame) obj;
        if (endOfStream != other.endOfStream) {
            return false;
        }
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        if (priority != other.priority) {
            return false;
        }
        if (streamId != other.streamId) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultHttp2HeadersFrame [streamId=" + streamId + ", priority=" + priority
                + ", endOfStream=" + endOfStream + ", headers=" + headers + "]";
    }

    public static class Builder {
        private int streamId;
        private int priority = DEFAULT_STREAM_PRIORITY;
        private Http2Headers.Builder headersBuilder = new Http2Headers.Builder();
        private boolean endOfStream;

        public Builder setStreamId(int streamId) {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be > 0.");
            }
            this.streamId = streamId;
            return this;
        }

        public Builder setEndOfStream(boolean endOfStream) {
            this.endOfStream = endOfStream;
            return this;
        }

        public Builder setPriority(int priority) {
            if (priority < 0) {
                throw new IllegalArgumentException("Priority must be >= 0");
            }
            this.priority = priority;
            return this;
        }

        public Http2Headers.Builder headers() {
            return headersBuilder;
        }

        public Builder setHeaders(Http2Headers headers) {
            this.headersBuilder.addHeaders(headers);
            return this;
        }

        public DefaultHttp2HeadersFrame build() {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be set.");
            }
            return new DefaultHttp2HeadersFrame(this);
        }
    }
}
