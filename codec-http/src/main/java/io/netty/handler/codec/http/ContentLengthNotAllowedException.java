/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.handler.codec.DecoderException;

/**
 * Thrown by {@link HttpObjectDecoder#handleTransferEncodingChunkedWithContentLength(HttpMessage)} by default.
 * <p>
 * The HTTP/1.1 specification, RFC 9112, disallow senders from including both {@code Tranfer-Encoding} and
 * {@code Content-Length headers in the same message, and permits servers to reject such requests.
 */
public final class ContentLengthNotAllowedException extends DecoderException {
    /**
     * Create a new instance with the given message.
     * @param message The exception message.
     */
    public ContentLengthNotAllowedException(String message) {
        super(message);
    }
}
