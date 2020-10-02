/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

public final class IOUring {

    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;

        if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
            cause = new UnsupportedOperationException(
                    "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
        } else {
            Throwable unsafeCause = PlatformDependent.getUnsafeUnavailabilityCause();
            if (unsafeCause == null) {
                RingBuffer ringBuffer = null;
                try {
                    ringBuffer = Native.createRingBuffer();
                    Native.checkAllIOSupported(ringBuffer.fd());
                } catch (Throwable t) {
                    cause = t;
                } finally {
                    if (ringBuffer != null) {
                        try {
                            ringBuffer.close();
                        } catch (Exception ignore) {
                            // ignore
                        }
                    }
                }
            } else {
                cause = new UnsupportedOperationException("Unsafe is not supported", unsafeCause);
            }
        }

        UNAVAILABILITY_CAUSE = cause;
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private IOUring() {
    }
}
