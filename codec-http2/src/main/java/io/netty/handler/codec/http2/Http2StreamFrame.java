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

/**
 * A frame whose meaning <em>may</em> apply to a particular stream, instead of the entire
 * connection. It is still possibly for this frame type to apply to the entire connection. In such
 * cases, the {@code stream} reference should be {@code null} or an object referring to the
 * connection.
 *
 * <p>The meaning of {@code stream} is context-dependent and may change as a frame is processed in
 * the pipeline.
 */
public interface Http2StreamFrame extends Http2Frame {
    /**
     * Set the stream identifier for this message.
     *
     * @return {@code this}
     */
    Http2StreamFrame setStream(Object stream);

    /**
     * The stream this frame applies to.
     */
    Object stream();
}
