/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.util.internal.PlatformDependent;

import javax.net.ssl.SSLParameters;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.AccessController;
import java.security.PrivilegedAction;

final class OpenSslParametersUtil {

    private static final MethodHandle GET_NAMED_GROUPS;
    private static final MethodHandle SET_NAMED_GROUPS;

    static {
        MethodHandle getNamedGroups = null;
        MethodHandle setNamedGroups = null;
        if (PlatformDependent.javaVersion() >= 20) {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            getNamedGroups = obtainHandle(lookup, "getNamedGroups",
                    MethodType.methodType(String[].class));
            setNamedGroups = obtainHandle(lookup, "setNamedGroups",
                    MethodType.methodType(void.class, String[].class));
        }
        GET_NAMED_GROUPS = getNamedGroups;
        SET_NAMED_GROUPS = setNamedGroups;
    }

    private static MethodHandle obtainHandle(final MethodHandles.Lookup lookup,
                                             final String methodName, final MethodType type) {
        return AccessController.doPrivileged((PrivilegedAction<MethodHandle>) () -> {
            try {
                return lookup.findVirtual(SSLParameters.class, methodName, type);
            } catch (UnsupportedOperationException | SecurityException |
                     NoSuchMethodException | IllegalAccessException e) {
                // Just ignore it.
                return null;
            }
        });
    }

    static String[] getNamesGroups(SSLParameters parameters) {
        if (GET_NAMED_GROUPS == null) {
            return null;
        }
        try {
            return (String[]) GET_NAMED_GROUPS.invoke(parameters);
        } catch (Throwable t) {
            // Just ignore it.
            return null;
        }
    }

    static void setNamesGroups(SSLParameters parameters, String[] names) {
        if (SET_NAMED_GROUPS == null) {
            return;
        }
        try {
            SET_NAMED_GROUPS.invoke(parameters, names);
        } catch (Throwable ignore) {
            // Ignore
        }
    }

    private OpenSslParametersUtil() { }
}
