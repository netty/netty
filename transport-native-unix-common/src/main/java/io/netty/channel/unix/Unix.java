/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.UnstableApi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tells if <a href="https://netty.io/wiki/native-transports.html">{@code netty-transport-native-unix}</a> is
 * supported.
 */
public final class Unix {
    private static final AtomicBoolean registered = new AtomicBoolean();

    static {
        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possiblity of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(Unix.class,
                // netty_unix_errors
                OutOfMemoryError.class, RuntimeException.class, ClosedChannelException.class,
                IOException.class, PortUnreachableException.class,

                // netty_unix_socket
                DatagramSocketAddress.class, InetSocketAddress.class
        );
    }

    /**
     * Internal method... Should never be called from the user.
     *
     * @param registerTask
     */
    @UnstableApi
    public static void registerInternal(Runnable registerTask) {
        if (registered.compareAndSet(false, true)) {
            registerTask.run();
            Socket.initialize();
        }
    }

    /**
     * Returns {@code true} if and only if the <a href="https://netty.io/wiki/native-transports.html">{@code
     * netty_transport_native_unix}</a> is available.
     */
    @Deprecated
    public static boolean isAvailable() {
        return false;
    }

    /**
     * Ensure that <a href="https://netty.io/wiki/native-transports.html">{@code netty_transport_native_unix}</a> is
     * available.
     *
     * @throws UnsatisfiedLinkError if unavailable
     */
    @Deprecated
    public static void ensureAvailability() {
       throw new UnsupportedOperationException();
    }

    /**
     * Returns the cause of unavailability of <a href="https://netty.io/wiki/native-transports.html">
     * {@code netty_transport_native_unix}</a>.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    @Deprecated
    public static Throwable unavailabilityCause() {
        return new UnsupportedOperationException();
    }

    private Unix() {
    }
}
