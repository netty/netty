/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.util.internal.StringUtil;

/**
 * Default implementation of {@link Http2PushPromiseRead}
 */
public class DefaultHttp2PushPromiseRead implements Http2PushPromiseRead {

    private final int promisedStreamId;
    private final Http2Headers http2Headers;
    private final int padding;
    private Http2FrameStream http2StreamFrame;

    public DefaultHttp2PushPromiseRead(int promisedStreamId, Http2Headers http2Headers, int padding) {
        this.promisedStreamId = promisedStreamId;
        this.http2Headers = http2Headers;
        this.padding = padding;
    }

    @Override
    public int promisedStreamId() {
        return promisedStreamId;
    }

    @Override
    public Http2Headers http2Headers() {
        return http2Headers;
    }

    @Override
    public int padding() {
        return padding;
    }

    @Override
    public Http2StreamFrame stream(Http2FrameStream stream) {
        http2StreamFrame = stream;
        return this;
    }

    @Override
    public Http2FrameStream stream() {
        return http2StreamFrame;
    }

    @Override
    public String name() {
        return "PUSH_PROMISE_READ";
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) +
                "(padding=" + padding +
                ", promisedStreamId=" + promisedStreamId +
                ", http2Headers=" + http2Headers +
                ", stream=" + http2StreamFrame +
                ')';
    }
}
