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

package io.netty5.handler.codec.h2new;

import io.netty5.buffer.api.Buffer;

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#DATA">HTTP/2 data frame</a>.
 */
public interface Http2DataFrame extends Http2RequestStreamFrame {

    /**
     * Returns the {@link Buffer} containing data for this frame.
     *
     * @return The {@link Buffer} containing data for this frame.
     */
    Buffer data();

    /**
     * Returns {@code true} if {@code PADDED} flag is set for this frame.
     *
     * @return {@code true} if {@code PADDED} flag is set for this frame.
     */
    default boolean isPadded() {
        return padding() > 0;
    }

    /**
     * Returns the padding, {@link #isPadded()}  if available}, otherwise negative.
     *
     * @return padding, {@link #isPadded()}  if available}, otherwise negative.
     */
    int padding();

    /**
     * Returns the number of bytes that are flow-controlled initially, so even if the {@link #data()} is consumed
     * this will not change.
     */
    int initialFlowControlledBytes();

    /**
     * Returns {@code true} if {@code END_STREAM} flag is set for this frame.
     *
     * @return {@code true} if {@code END_STREAM} flag is set for this frame.
     */
    boolean isEndStream();
}
