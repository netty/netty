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

/**
 * An <a href="https://httpwg.org/specs/rfc7540.html#RST_STREAM">HTTP/2 reset stream frame</a>.
 */
public interface Http2ResetStreamFrame extends Http2RequestStreamFrame {

    /**
     * Returns the reason for resetting the stream, represented as an HTTP/2 error code.
     *
     * @return The reason for resetting the stream, represented as an HTTP/2 error code.
     */
    long errorCode();
}
