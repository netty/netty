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
package io.netty.channel;

import io.netty.util.internal.ThrowableUtil;

import java.nio.channels.ClosedChannelException;

/**
 * Cheap {@link ClosedChannelException} that does not fill in the stacktrace.
 */
final class StacklessClosedChannelException extends ClosedChannelException {

    private static final long serialVersionUID = -2214806025529435136L;

    private StacklessClosedChannelException() { }

    @Override
    public Throwable fillInStackTrace() {
        // Suppress a warning since this method doesn't need synchronization
        return this; // lgtm [java/non-sync-override]
    }

    /**
     * Creates a new {@link StacklessClosedChannelException} which has the origin of the given {@link Class} and method.
     */
    static StacklessClosedChannelException newInstance(Class<?> clazz, String method) {
        return ThrowableUtil.unknownStackTrace(new StacklessClosedChannelException(), clazz, method);
    }
}
