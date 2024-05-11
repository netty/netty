/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.StandardProtocolFamily;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

final class SelectorProviderUtil {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SelectorProviderUtil.class);

    static Method findOpenMethod(String methodName) {
        if (PlatformDependent.javaVersion() >= 15) {
            try {
                return SelectorProvider.class.getMethod(methodName, java.net.ProtocolFamily.class);
            } catch (Throwable e) {
                logger.debug("SelectorProvider.{}(ProtocolFamily) not available, will use default", methodName, e);
            }
        }
        return null;
    }

    /**
     * Use the {@link SelectorProvider} to open {@link SocketChannel} and so remove condition in
     * {@link SelectorProvider#provider()} which is called by each SocketChannel.open() otherwise.
     * <p>
     * See <a href="https://github.com/netty/netty/issues/2308">#2308</a>.
     */
    private static <C extends Channel> C newChannel(Method method, SelectorProvider provider,
                                                    Object family) throws IOException {
        if (family != null && method != null) {
            try {
                @SuppressWarnings("unchecked")
                C channel = (C) method.invoke(provider, family);
                return channel;
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }
        return null;
    }

    static <C extends Channel> C newChannel(Method method, SelectorProvider provider,
                                                    SocketProtocolFamily family) throws IOException {
        if (family != null) {
            return newChannel(method, provider, family.toJdkFamily());
        }
        return null;
    }

    static <C extends Channel> C newDomainSocketChannel(Method method, SelectorProvider provider) throws IOException {
        return newChannel(method, provider, StandardProtocolFamily.valueOf("UNIX"));
    }

    private SelectorProviderUtil() { }
}
