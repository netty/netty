/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

/**
 * HTTP/2 Push Promise Frame
 */
public interface Http2PushPromiseFrame extends Http2StreamFrame {

    /**
     * Set the Promise {@link Http2FrameStream} object for this frame.
     */
    Http2StreamFrame pushStream(Http2FrameStream stream);

    /**
     * Returns the Promise {@link Http2FrameStream} object for this frame, or {@code null} if the
     * frame has yet to be associated with a stream.
     */
    Http2FrameStream pushStream();

    /**
     * {@link Http2Headers} sent in Push Promise
     */
    Http2Headers http2Headers();

    /**
     * Frame padding to use. Will be non-negative and less than 256.
     */
    int padding();

    /**
     * Promised Stream ID
     */
    int promisedStreamId();

    @Override
    Http2PushPromiseFrame stream(Http2FrameStream stream);

}
