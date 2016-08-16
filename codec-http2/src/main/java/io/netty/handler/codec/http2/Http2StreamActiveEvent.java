/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.UnstableApi;

/**
 * This event is emitted by the {@link Http2FrameCodec} when a stream becomes active.
 */
@UnstableApi
public class Http2StreamActiveEvent extends AbstractHttp2StreamStateEvent {

    private final Http2HeadersFrame headers;

    public Http2StreamActiveEvent(int streamId) {
        this(streamId, null);
    }

    public Http2StreamActiveEvent(int streamId, Http2HeadersFrame headers) {
        super(streamId);
        this.headers = headers;
    }

    /**
     * For outbound streams, this method returns the <em>same</em> {@link Http2HeadersFrame} object as the one that
     * made the stream active. For inbound streams, this method returns {@code null}.
     */
    public Http2HeadersFrame headers() {
        return headers;
    }
}
