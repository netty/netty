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
package io.netty.handler.codec.http2.internal;

import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.util.internal.ThrowableUtil;

public final class StacklessHttp2Exception extends Http2Exception {

    private static final long serialVersionUID = 1077888485687219443L;

    StacklessHttp2Exception(Http2Error error, String message, ShutdownHint shutdownHint) {
        super(error, message, shutdownHint);
    }

    public StacklessHttp2Exception(Http2Error error, String message, ShutdownHint shutdownHint, boolean shared) {
        super(error, message, shutdownHint, shared);
    }

    // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
    // Classloader.
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public static Http2Exception newStatic(Http2Error error, String message, ShutdownHint shutdownHint,
                                    Class<?> clazz, String method) {
        final Http2Exception exception = new StacklessHttp2Exception(error, message, shutdownHint, true);
        return ThrowableUtil.unknownStackTrace(exception, clazz, method);
    }
}
