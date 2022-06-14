

/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel;

import io.netty5.util.internal.ThrowableUtil;

import java.io.IOException;

/**
 * Used to fail pending writes when a channel's output has been shutdown.
 */
public class ChannelOutputShutdownException extends IOException {
    private static final long serialVersionUID = 6712549938359321378L;

    public ChannelOutputShutdownException() { }

    public ChannelOutputShutdownException(String msg) {
        super(msg);
    }

    public ChannelOutputShutdownException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Creates a new {@link ChannelOutputShutdownException} which has the origin of the given {@link Class} and method.
     */
    static ChannelOutputShutdownException newInstance(Class<?> clazz, String method) {
        return ThrowableUtil.unknownStackTrace(new ChannelOutputShutdownException() {
            @Override
            public Throwable fillInStackTrace() {
                // Suppress a warning since this method doesn't need synchronization
                return this; // lgtm [java/non-sync-override]
            }
        }, clazz, method);
    }
}
