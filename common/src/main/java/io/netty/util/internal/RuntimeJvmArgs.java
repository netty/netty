/*
 * Copyright 2026 The Netty Project
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
package io.netty.util.internal;

import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This allows avoiding class verification failures on older Android runtimes.
 * Android does not have {@link java.lang.management.ManagementFactory}, so loading this class
 * on Android will fail with {@link NoClassDefFoundError} — callers must catch {@link Throwable}.
 */
final class RuntimeJvmArgs {

    private RuntimeJvmArgs() {
    }

    public static long parseMaxDirectMemorySize(long maxDirectMemory) {
        if (PlatformDependent.isAndroid()) {
            return maxDirectMemory;
        }
        if (PlatformDependent.isJ9Jvm()) {
            return parseMaxDirectMemorySizeVmArgs(maxDirectMemory);
        }
        return parseMaxDirectMemorySizeVmOption(maxDirectMemory);
    }

    private static long parseMaxDirectMemorySizeVmOption(long maxDirectMemory) {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (bean != null) {
                long value = Long.parseLong(bean.getVMOption("MaxDirectMemorySize").getValue());
                if (value > 0) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        return maxDirectMemory;
    }

    private static long parseMaxDirectMemorySizeVmArgs(long maxDirectMemory) {
        try {
            List<String> vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            Pattern maxDirectMemorySizeArgPattern = Pattern
                    .compile("\\s*-XX:MaxDirectMemorySize\\s*=\\s*([0-9]+)\\s*([kKmMgG]?)\\s*$");

            for (int i = vmArgs.size() - 1; i >= 0; i--) {
                Matcher m = maxDirectMemorySizeArgPattern.matcher(vmArgs.get(i));
                if (!m.matches()) {
                    continue;
                }

                maxDirectMemory = Long.parseLong(m.group(1));
                switch (m.group(2).charAt(0)) {
                    case 'k':
                    case 'K':
                        maxDirectMemory *= 1024;
                        break;
                    case 'm':
                    case 'M':
                        maxDirectMemory *= 1024 * 1024;
                        break;
                    case 'g':
                    case 'G':
                        maxDirectMemory *= 1024 * 1024 * 1024;
                        break;
                    default:
                        break;
                }
                break;
            }
        } catch (Throwable ignored) {
            // Ignore
        }
        return maxDirectMemory;
    }
}
