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
package io.netty.channel.uring;

import io.netty.channel.ChannelOption;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class IoUring {

    private static final Throwable UNAVAILABILITY_CAUSE;
    private static final boolean IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED;
    private static final boolean IORING_SPLICE_SUPPORTED;
    private static final boolean IORING_ACCEPT_NO_WAIT_SUPPORTED;
    private static final boolean IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED;
    private static final boolean IORING_SETUP_SUBMIT_ALL_SUPPORTED;

    static {
        Throwable cause = null;
        boolean socketNonEmptySupported = false;
        boolean spliceSupported = false;
        boolean acceptSupportNoWait = false;
        boolean registerIowqWorkersSupported = false;
        boolean submitAllSupported = false;
        try {
            if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
                cause = new UnsupportedOperationException(
                        "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
            } else {
                String kernelVersion = Native.kernelVersion();
                Native.checkKernelVersion(kernelVersion);
                Throwable unsafeCause = PlatformDependent.getUnsafeUnavailabilityCause();
                if (unsafeCause == null) {
                    RingBuffer ringBuffer = null;
                    try {
                        ringBuffer = Native.createRingBuffer(1, 0);
                        Native.checkAllIOSupported(ringBuffer.fd());
                        socketNonEmptySupported = Native.isIOUringCqeFSockNonEmptySupported(ringBuffer.fd());
                        spliceSupported = Native.isIOUringSupportSplice(ringBuffer.fd());
                        // IORING_FEAT_RECVSEND_BUNDLE was added in the same release.
                        acceptSupportNoWait = (ringBuffer.features() & Native.IORING_FEAT_RECVSEND_BUNDLE) != 0;
                        registerIowqWorkersSupported = Native.isRegisterIOWQWorkerSupported(ringBuffer.fd());
                        submitAllSupported = Native.ioUringSetupSupportsFlags(Native.IORING_SETUP_SUBMIT_ALL);
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
            InternalLogger logger = InternalLoggerFactory.getInstance(IoUring.class);
            if (logger.isTraceEnabled()) {
                logger.debug("IoUring support is not available", cause);
            } else if (logger.isDebugEnabled()) {
                logger.debug("IoUring support is not available: {}", cause.getMessage());
            }
        }
        UNAVAILABILITY_CAUSE = cause;
        IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED = socketNonEmptySupported;
        IORING_SPLICE_SUPPORTED = spliceSupported;
        IORING_ACCEPT_NO_WAIT_SUPPORTED = acceptSupportNoWait;
        IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED = registerIowqWorkersSupported;
        IORING_SETUP_SUBMIT_ALL_SUPPORTED = submitAllSupported;
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Returns {@code true} if the io_uring native transport is both {@linkplain #isAvailable() available} and supports
     * {@linkplain ChannelOption#TCP_FASTOPEN_CONNECT client-side TCP FastOpen}.
     *
     * @return {@code true} if it's possible to use client-side TCP FastOpen via io_uring, otherwise {@code false}.
     */
    public static boolean isTcpFastOpenClientSideAvailable() {
        return isAvailable() && Native.IS_SUPPORTING_TCP_FASTOPEN_CLIENT;
    }

    /**
     * Returns {@code true} if the io_uring native transport is both {@linkplain #isAvailable() available} and supports
     * {@linkplain ChannelOption#TCP_FASTOPEN server-side TCP FastOpen}.
     *
     * @return {@code true} if it's possible to use server-side TCP FastOpen via io_uring, otherwise {@code false}.
     */
    public static boolean isTcpFastOpenServerSideAvailable() {
        return isAvailable() && Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;
    }

    static boolean isIOUringCqeFSockNonEmptySupported() {
        return IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED;
    }

    static boolean isIOUringSpliceSupported() {
        return IORING_SPLICE_SUPPORTED;
    }

    static boolean isIOUringAcceptNoWaitSupported() {
        return IORING_ACCEPT_NO_WAIT_SUPPORTED;
    }

    static boolean isRegisterIowqMaxWorkersSupported() {
        return IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED;
    }

    static boolean isIOUringSetupSubmitAllSupported() {
        return IORING_SETUP_SUBMIT_ALL_SUPPORTED;
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
