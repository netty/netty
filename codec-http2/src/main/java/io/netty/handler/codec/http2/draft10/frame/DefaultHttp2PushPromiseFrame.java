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

import io.netty.handler.codec.http2.draft10.Http2Headers;

public final class DefaultHttp2PushPromiseFrame implements Http2PushPromiseFrame {

    private final int streamId;
    private final int promisedStreamId;
    private final Http2Headers headers;

    private DefaultHttp2PushPromiseFrame(Builder builder) {
        streamId = builder.streamId;
        promisedStreamId = builder.promisedStreamId;
        headers = builder.headers;
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
    public int getPromisedStreamId() {
        return promisedStreamId;
    }

    @Override
    public Http2Headers getHeaders() {
        return headers;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((headers == null) ? 0 : headers.hashCode());
        result = prime * result + promisedStreamId;
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
        DefaultHttp2PushPromiseFrame other = (DefaultHttp2PushPromiseFrame) obj;
        if (headers == null) {
            if (other.headers != null) {
                return false;
            }
        } else if (!headers.equals(other.headers)) {
            return false;
        }
        if (promisedStreamId != other.promisedStreamId) {
            return false;
        }
        if (streamId != other.streamId) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "DefaultHttp2PushPromiseFrame [streamId=" + streamId + ", promisedStreamId="
                + promisedStreamId + ", headers=" + headers + ']';
    }

    public static class Builder {
        private int streamId;
        private int promisedStreamId;
        private Http2Headers headers;

        public Builder setStreamId(int streamId) {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be > 0.");
            }
            this.streamId = streamId;
            return this;
        }

        public Builder setPromisedStreamId(int promisedStreamId) {
            if (promisedStreamId <= 0) {
                throw new IllegalArgumentException("promisedStreamId must be > 0.");
            }
            this.promisedStreamId = promisedStreamId;
            return this;
        }

        public Builder setHeaders(Http2Headers headers) {
            if (headers == null) {
                throw new IllegalArgumentException("headers must not be null.");
            }
            this.headers = headers;
            return this;
        }

        public DefaultHttp2PushPromiseFrame build() {
            if (streamId <= 0) {
                throw new IllegalArgumentException("StreamId must be set.");
            }
            if (promisedStreamId <= 0) {
                throw new IllegalArgumentException("promisedStreamId must be set.");
            }
            if (headers == null) {
                throw new IllegalArgumentException("headers must be set.");
            }
            return new DefaultHttp2PushPromiseFrame(this);
        }
    }
}
