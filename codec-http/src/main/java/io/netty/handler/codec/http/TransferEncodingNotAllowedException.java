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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.ThrowableUtil;

/**
 * Thrown by {@link HttpObjectDecoder} when an HTTP message uses a protocol version older than {@code HTTP/1.1}
 * and includes an {@code Transfer-Encoding} header.
 */
public final class TransferEncodingNotAllowedException extends DecoderException {
    /**
     * Create a new instance with the given message.
     * @param message The exception message.
     */
    public TransferEncodingNotAllowedException(String message) {
        super(message);
    }

    @SuppressJava6Requirement(reason = "uses Java 7+ DecoderException.<init>(String, Throwable, boolean, boolean)" +
            " but is guarded by version checks")
    private TransferEncodingNotAllowedException(String message, boolean shared) {
        // A shared instance must not populate a (native) backtrace: doing so would pin the Classloader of whatever
        // thread happened to trigger its creation (see https://github.com/netty/netty/issues/17128).
        super(message, null, false, false);
        assert shared;
    }

    static TransferEncodingNotAllowedException newStatic(String message, Class<?> clazz, String method) {
        if (PlatformDependent.javaVersion() >= 7) {
            return new TransferEncodingNotAllowedException(message, true);
        }
        // Java 6 has no way to disable the stack trace, so only replace the visible one.
        return ThrowableUtil.unknownStackTrace(new TransferEncodingNotAllowedException(message), clazz, method);
    }
}
