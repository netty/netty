/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.h2new;

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#FrameHeader">HTTP/2 frame</a>.
 */
public interface Http2Frame {

    /**
     * <a href="https://httpwg.org/specs/rfc7540.html#FrameTypes">HTTP/2 frame type</a>
     */
    enum Type {
        Data,
        Headers,
        Priority,
        RstStream,
        Settings,
        PushPromise,
        Ping,
        GoAway,
        WindowUpdate,
        Unknown
    }

    /**
     * Returns the <a href="https://httpwg.org/specs/rfc7540.html#FrameTypes">Type</a> of this frame.
     *
     * @return <a href="https://httpwg.org/specs/rfc7540.html#FrameTypes">Type</a> of this frame.
     */
    Type frameType();

    /**
     * Returns the identifier for the stream on which this frame was sent/received.
     * @return Identifier for the stream on which this frame was sent/received.
     */
    int streamId();
}
