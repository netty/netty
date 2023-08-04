/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.unix.tests;

import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.PlatformDependent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class UnixTestUtils {
    private static final Object INET_LOOPBACK_UNAVAILABLE = new Object();
    private static volatile Object inetLoopbackCache;

    /**
     * @deprecated Use {@link #newDomainSocketAddress()} instead.
     */
    @Deprecated
    public static DomainSocketAddress newSocketAddress() {
        return newDomainSocketAddress();
    }

    public static DomainSocketAddress newDomainSocketAddress() {
        try {
            File file;
            do {
                file = PlatformDependent.createTempFile("NETTY", "UDS", null);
                if (!file.delete()) {
                    throw new IOException("failed to delete: " + file);
                }
            } while (file.getAbsolutePath().length() > 128);
            return new DomainSocketAddress(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The JDK method may produce IPv4 loopback addresses where {@link io.netty.util.NetUtil#LOCALHOST} might be an
     * IPv6 addresses.
     * This difference can stress the system in different ways that are important to test.
     */
    public static SocketAddress newInetLoopbackSocketAddress() {
        Object loopback = inetLoopbackCache;

        if (loopback == null) {
            inetLoopbackCache = loopback = getLoopbackAddress();
        }

        assumeTrue(loopback != INET_LOOPBACK_UNAVAILABLE, "InetAddress.getLoopbackAddress() is not available");
        return new InetSocketAddress((InetAddress) loopback, 0);
    }

    private static Object getLoopbackAddress() {
        try {
            Method method = InetAddress.class.getMethod("getLoopbackAddress");
            return method.invoke(null);
        } catch (Exception ignore) {
            return INET_LOOPBACK_UNAVAILABLE;
        }
    }

    private UnixTestUtils() { }
}
