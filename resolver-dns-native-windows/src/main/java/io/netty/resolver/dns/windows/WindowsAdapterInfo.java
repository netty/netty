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

package io.netty.resolver.dns.windows;

import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;

final class WindowsAdapterInfo {
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;
        try {
            loadNativeLibrary();
        } catch (Throwable error) {
            cause = error;
        }
        UNAVAILABILITY_CAUSE = cause;
    }

    private WindowsAdapterInfo() {
        // Utility
    }

    private static void loadNativeLibrary() {
        if (!PlatformDependent.isWindows()) {
            throw new IllegalStateException("Only supported on Windows");
        }

        String sharedLibName = "netty_resolver_dns_native_windows" + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(WindowsAdapterInfo.class);
        NativeLibraryLoader.load(sharedLibName, cl);
    }

    static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    static native NetworkAdapter[] adapters();
}
