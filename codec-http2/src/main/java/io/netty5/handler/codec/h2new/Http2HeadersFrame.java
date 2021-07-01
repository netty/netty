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

import io.netty.handler.codec.http2.Http2Headers;

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#HEADERS">HTTP/2 headers frame</a>.
 */
public interface Http2HeadersFrame extends Http2RequestStreamFrame {
    /**
     * Returns the {@link Http2Headers} contained in this frame.
     *
     * @return The {@link Http2Headers} contained in this frame.
     */
    Http2Headers headers();

    /**
     * Returns {@code true} if {@link #isExclusiveDependency()}, {@link #streamDependency()} and {@link #weight()}
     * are set for this frame.
     *
     * @return {@code true} if {@link #isExclusiveDependency()}, {@link #streamDependency()} and {@link #weight()}
     * are set for this frame.
     */
    boolean isPrioritySet();

    /**
     * Returns {@code true} if {@link #streamDependency() stream dependency} is an exclusive dependency.
     *
     * @return {@code true} if {@link #streamDependency() stream dependency} is an exclusive dependency.
     */
    boolean isExclusiveDependency();

    /**
     * Returns a positive number indicating the stream dependency, {@link #isPrioritySet() if available}, otherwise
     * returns a negative number.
     *
     * @return A positive number indicating the stream dependency, {@link #isPrioritySet() if available}, otherwise
     * returns a negative number.
     */
    int streamDependency();

    /**
     * Returns a number between 1 and 256 (both inclusive) indicating the stream weight,
     * {@link #isPrioritySet() if available}, otherwise returns a negative number.
     *
     * @return A number between 1 and 256 (both inclusive) indicating the stream weight,
     * {@link #isPrioritySet() if available}, otherwise returns a negative number.
     */
    short weight();

    /**
     * Returns {@code true} if {@code END_STREAM} flag is set for this frame.
     *
     * @return {@code true} if {@code END_STREAM} flag is set for this frame.
     */
    boolean isEndStream();

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
}
