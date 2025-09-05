/*
 * Copyright 2025 The Netty Project
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

import io.netty.handler.codec.CorruptedFrameException;

/**
 * Thrown when HTTP chunk extensions could not be parsed, typically due to incorrect use of CR LF delimiters.
 * <p>
 * <a href="https://datatracker.ietf.org/doc/html/rfc9112#name-chunked-transfer-coding">RFC 9112</a>
 * specifies that chunk header lines must be terminated in a CR LF pair,
 * and that a lone LF octet is not allowed within the chunk header line.
 */
public final class InvalidChunkExtensionException extends CorruptedFrameException {
    private static final long serialVersionUID = 536224937231200736L;

    public InvalidChunkExtensionException() {
        super("Line Feed must be preceded by Carriage Return when terminating HTTP chunk header lines");
    }

    public InvalidChunkExtensionException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidChunkExtensionException(String message) {
        super(message);
    }

    public InvalidChunkExtensionException(Throwable cause) {
        super(cause);
    }
}
