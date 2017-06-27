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
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;

import java.util.Locale;

/**
 * If KQueue is available the JNI resources will be loaded when this class loads.
 */
@UnstableApi
public final class KQueue {
    private static final Throwable UNAVAILABILITY_CAUSE;

    static  {
        Throwable cause = null;
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

        if (cause != null) {
            UNAVAILABILITY_CAUSE = cause;
        } else {
            cause = PlatformDependent.hasUnsafe() ? null :
                    new IllegalStateException("sun.misc.Unsafe not available");
            if (cause == null) {
                if (SystemPropertyUtil.get("os.name", "").toLowerCase(Locale.US).contains("mac")) {
                    String version = SystemPropertyUtil.get("os.version", "");
                    String[] versionParts = SystemPropertyUtil.get("os.version", "")
                            .toLowerCase(Locale.US).split("\\.");
                    if (versionParts.length < 2) {
                        cause = new IllegalStateException("MacOS version " + version + " not supported");
                    } else {
                        try {
                            int majorVersion = Integer.parseInt(versionParts[0]);
                            int minorVersion = Integer.parseInt(versionParts[1]);
                            if (majorVersion < 10 || (majorVersion == 10 && minorVersion < 12)) {
                                cause = new IllegalStateException("MacOS version " + version + " not supported");
                            }
                        } catch (NumberFormatException e) {
                            cause = new IllegalStateException("MacOS version " + version + " not supported", e);
                        }
                    }
                }
            }
            UNAVAILABILITY_CAUSE = cause;
        }
    }

    /**
     * Returns {@code true} if and only if the
     * <a href="http://netty.io/wiki/native-transports.html">{@code netty-transport-native-kqueue}</a> is available.
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Ensure that <a href="http://netty.io/wiki/native-transports.html">{@code netty-transport-native-kqueue}</a> is
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
     * Returns the cause of unavailability of
     * <a href="http://netty.io/wiki/native-transports.html">{@code netty-transport-native-kqueue}</a>.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private KQueue() { }
}
