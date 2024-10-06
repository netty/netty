/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IoUring {
    private static final Logger logger = LoggerFactory.getLogger(IoUring.class);
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;
        try {
            if (SystemPropertyUtil.getBoolean("io.netty5.transport.noNative", false)) {
                cause = new UnsupportedOperationException(
                        "Native transport was explicit disabled with -Dio.netty5.transport.noNative=true");
            } else {
                String kernelVersion = Native.kernelVersion();
                Native.checkKernelVersion(kernelVersion);
                Throwable unsafeCause = PlatformDependent.getUnsafeUnavailabilityCause();
                if (unsafeCause == null) {
                    RingBuffer ringBuffer = null;
                    try {
                        ringBuffer = Native.createRingBuffer();
                        Native.checkAllIOSupported(ringBuffer.fd());
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
        } catch (Throwable t) {
            cause = t;
        }
        if (cause != null) {
            if (logger.isTraceEnabled()) {
                logger.debug("io_uring integration unavailable: {}", cause.getMessage(), cause);
            } else {
                logger.debug("io_uring integration unavailable: {}", cause.getMessage());
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

    private IoUring() {
    }
}
