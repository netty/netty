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
package io.netty.incubator.codec.http3;

/**
 * Thrown if {@link Http3Headers} validation fails for some reason.
 */
public final class Http3HeadersValidationException extends RuntimeException {
    public Http3HeadersValidationException(String message) {
        super(message);
    }

    public Http3HeadersValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
