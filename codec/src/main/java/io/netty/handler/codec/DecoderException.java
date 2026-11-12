/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec;

import io.netty.util.internal.SuppressJava6Requirement;

/**
 * An {@link CodecException} which is thrown by a decoder.
 */
public class DecoderException extends CodecException {

    private static final long serialVersionUID = 6926716840699621852L;

    /**
     * Creates a new instance.
     */
    public DecoderException() {
    }

    /**
     * Creates a new instance.
     */
    public DecoderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new instance.
     */
    public DecoderException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public DecoderException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new instance with the given suppression and stack trace settings, see
     * {@link RuntimeException#RuntimeException(String, Throwable, boolean, boolean)}.
     * <p>
     * This constructor requires Java 7+ and must only be invoked after checking
     * {@code PlatformDependent.javaVersion() >= 7}.
     */
    @SuppressJava6Requirement(reason = "uses Java 7+ RuntimeException.<init>(String, Throwable, boolean, boolean)" +
            " but callers are guarded by version checks")
    protected DecoderException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
