/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.kqueue;

import io.netty.channel.unix.FileDescriptor;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

/**
 * If KQueue is available the JNI resources will be loaded when this class loads.
 */
@UnstableApi
public final class KQueue {
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;
        if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
            cause = new UnsupportedOperationException(
                    "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
        } else {
            FileDescriptor kqueueFd = null;
            try {
                kqueueFd = Native.newKQueue();
            } catch (Throwable t) {
                cause = t;
            } finally {
                if (kqueueFd != null) {
                    try {
                        kqueueFd.close();
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
            }
        }

        UNAVAILABILITY_CAUSE = cause;
    }

    /**
     * Returns {@code true} if and only if the <a href="https://netty.io/wiki/native-transports.html">{@code
     * netty-transport-native-kqueue}</a> is available.
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Ensure that <a href="https://netty.io/wiki/native-transports.html">{@code netty-transport-native-kqueue}</a> is
     * available.
     *
     * @throws UnsatisfiedLinkError if unavailable
     */
    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    /**
     * Returns the cause of unavailability of <a href="https://netty.io/wiki/native-transports.html">{@code
     * netty-transport-native-kqueue}</a>.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private KQueue() {
    }
}
