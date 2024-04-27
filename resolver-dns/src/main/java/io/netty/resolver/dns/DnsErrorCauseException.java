/*
 * Copyright 2023 The Netty Project
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

package io.netty.resolver.dns;

import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.util.internal.ThrowableUtil;

import java.net.UnknownHostException;

/**
 * A metadata carrier exception, to propagate {@link DnsResponseCode} information as an enrichment
 * within the {@link UnknownHostException} cause.
 */
public final class DnsErrorCauseException extends RuntimeException {

    private static final long serialVersionUID = 7485145036717494533L;

    private final DnsResponseCode code;

    private DnsErrorCauseException(String message, DnsResponseCode code, boolean shared) {
        super(message, null, false, true);
        this.code = code;
        assert shared;
    }

    // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
    // Classloader.
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Returns the DNS error-code that caused the {@link UnknownHostException}.
     *
     * @return the DNS error-code that caused the {@link UnknownHostException}.
     */
    public DnsResponseCode getCode() {
        return code;
    }

    static DnsErrorCauseException newStatic(String message, DnsResponseCode code, Class<?> clazz, String method) {
        final DnsErrorCauseException exception = new DnsErrorCauseException(message, code, true);
        return ThrowableUtil.unknownStackTrace(exception, clazz, method);
    }
}
