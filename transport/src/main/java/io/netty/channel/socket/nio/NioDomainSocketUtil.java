/*
 * Copyright 2024 The Netty Project
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.nio.file.Path;

final class NioDomainSocketUtil {

    private static final Method OF_METHOD;
    private static final Method GET_PATH_METHOD;

    static {
        Method ofMethod;
        Method getPathMethod;
        try {
            Class<?> clazz = Class.forName("java.net.UnixDomainSocketAddress");
            ofMethod = clazz.getMethod("of", String.class);
            getPathMethod = clazz.getMethod("getPath");

        } catch (Throwable error) {
            ofMethod = null;
            getPathMethod = null;
        }
        OF_METHOD = ofMethod;
        GET_PATH_METHOD = getPathMethod;
    }

    static SocketAddress newUnixDomainSocketAddress(String path) {
        if (OF_METHOD == null) {
            throw new IllegalStateException();
        }
        try {
            return (SocketAddress) OF_METHOD.invoke(null, path);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    static void deleteSocketFile(SocketAddress address) {
        if (GET_PATH_METHOD == null) {
            throw new IllegalStateException();
        }
        try {
            Path path = (Path) GET_PATH_METHOD.invoke(address);
            if (path != null) {
                path.toFile().delete();
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    private NioDomainSocketUtil() { }
}
