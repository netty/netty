/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.netty.util.internal;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Avoid class verification failures on Android.
 * Android does not have {@link ManagementFactory},
 * so loading this class on Android will fail with
 * {@link NoClassDefFoundError}.
 */
final class RuntimeJvmArgs {

    private static final String MAX_DIRECT_MEMORY_SIZE_ARG =
            "-XX:MaxDirectMemorySize=";

    private RuntimeJvmArgs() {
    }

    static long parseMaxDirectMemorySize(final long maxDirectMemory) {
        try {
            List<String> vmArgs = ManagementFactory
                    .getRuntimeMXBean()
                    .getInputArguments();
            for (int i = vmArgs.size() - 1; i >= 0; i--) {
                String arg = vmArgs.get(i);
                if (!arg.startsWith(MAX_DIRECT_MEMORY_SIZE_ARG)) {
                    continue;
                }
                return parseSize(arg, MAX_DIRECT_MEMORY_SIZE_ARG.length());
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        return maxDirectMemory;
    }

    private static long parseSize(final String arg, final int offset) {
        String val = arg.substring(offset).trim();
        if (val.isEmpty()) {
            return -1;
        }
        char lastChar = val.charAt(val.length() - 1);
        long multiplier;
        switch (lastChar) {
            case 'k':
            case 'K':
                multiplier = 1024;
                break;
            case 'm':
            case 'M':
                multiplier = 1024 * 1024;
                break;
            case 'g':
            case 'G':
                multiplier = 1024L * 1024 * 1024;
                break;
            default:
                return Long.parseLong(val);
        }
        String numStr = val.substring(0, val.length() - 1);
        return Long.parseLong(numStr) * multiplier;
    }
}
