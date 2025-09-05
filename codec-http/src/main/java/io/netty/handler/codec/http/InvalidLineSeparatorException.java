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

import io.netty.handler.codec.DecoderException;

/**
 * Thrown when {@linkplain HttpDecoderConfig#isStrictLineParsing() strict line parsing} is enabled,
 * and HTTP start- and header field-lines are not seperated by CR LF octet pairs.
 * <p>
 * Strict line parsing is enabled by default since Netty 4.1.124 and 4.2.4.
 * This default can be overridden by setting the {@value HttpObjectDecoder#PROP_DEFAULT_STRICT_LINE_PARSING} system
 * property to {@code false}.
 * <p>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc9112#name-message-format">RFC 9112 Section 2.1</a>.
 */
public final class InvalidLineSeparatorException extends DecoderException {
    private static final long serialVersionUID = 536224937231200736L;

    public InvalidLineSeparatorException() {
        super("Line Feed must be preceded by Carriage Return when terminating HTTP start- and header field-lines");
    }

    public InvalidLineSeparatorException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidLineSeparatorException(String message) {
        super(message);
    }

    public InvalidLineSeparatorException(Throwable cause) {
        super(cause);
    }
}
