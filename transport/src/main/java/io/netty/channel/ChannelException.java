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
package io.netty.channel;

import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.UnstableApi;

/**
 * A {@link RuntimeException} which is thrown when an I/O operation fails.
 */
public class ChannelException extends RuntimeException {

    private static final long serialVersionUID = 2908618315971075004L;

    /**
     * Creates a new exception.
     */
    public ChannelException() {
    }

    /**
     * Creates a new exception.
     */
    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     */
    public ChannelException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     */
    public ChannelException(Throwable cause) {
        super(cause);
    }

    protected ChannelException(String message, Throwable cause, boolean shared) {
        super(message, cause, false, true);
        assert shared;
    }

    static ChannelException newStatic(String message, Class<?> clazz, String method) {
        ChannelException exception = new StacklessChannelException(message, null, true);
        return ThrowableUtil.unknownStackTrace(exception, clazz, method);
    }

    private static final class StacklessChannelException extends ChannelException {
        private static final long serialVersionUID = -6384642137753538579L;

        StacklessChannelException(String message, Throwable cause, boolean shared) {
            super(message, cause, shared);
        }

        // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
        // Classloader.

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
