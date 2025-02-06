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
    private static final boolean IORING_SETUP_SINGLE_ISSUER_SUPPORTED;
    private static final boolean IORING_SETUP_DEFER_TASKRUN_SUPPORTED;
    private static final boolean IORING_REGISTER_BUFFER_RING_SUPPORTED;
    private static final boolean IORING_REGISTER_BUFFER_RING_INC_SUPPORTED;

    private static final InternalLogger logger;

    static {
        logger = InternalLoggerFactory.getInstance(IoUring.class);
        Throwable cause = null;
        boolean socketNonEmptySupported = false;
        boolean spliceSupported = false;
        boolean acceptSupportNoWait = false;
        boolean registerIowqWorkersSupported = false;
        boolean submitAllSupported = false;
        boolean singleIssuerSupported = false;
        boolean deferTaskrunSupported = false;
        boolean registerBufferRingSupported = false;
        boolean registerBufferRingIncSupported = false;

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
                        singleIssuerSupported = Native.ioUringSetupSupportsFlags(Native.IORING_SETUP_SINGLE_ISSUER);
                        // IORING_SETUP_DEFER_TASKRUN requires to also set IORING_SETUP_SINGLE_ISSUER.
                        // See https://manpages.debian.org/unstable/liburing-dev/io_uring_setup.2.en.html
                        deferTaskrunSupported = Native.ioUringSetupSupportsFlags(
                                Native.IORING_SETUP_SINGLE_ISSUER | Native.IORING_SETUP_DEFER_TASKRUN);
                        registerBufferRingSupported = Native.isRegisterBufferRingSupported(ringBuffer.fd(), 0);
                        registerBufferRingIncSupported = Native.isRegisterBufferRingSupported(ringBuffer.fd(),
                                Native.IOU_PBUF_RING_INC);
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
                logger.debug("IoUring support is not available", cause);
            } else if (logger.isDebugEnabled()) {
                logger.debug("IoUring support is not available: {}", cause.getMessage());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("IoUring support is available (" +
                        "IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED={}, " +
                        "IORING_SPLICE_SUPPORTED={}, " +
                        "IORING_ACCEPT_NO_WAIT_SUPPORTED={}, " +
                        "IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED={}, " +
                        "IORING_SETUP_SUBMIT_ALL_SUPPORTED={}, " +
                        "IORING_SETUP_SINGLE_ISSUER_SUPPORTED={}, " +
                        "IORING_SETUP_DEFER_TASKRUN_SUPPORTED={}, " +
                        "IORING_REGISTER_BUFFER_RING_SUPPORTED={}, " +
                        "IOU_PBUF_RING_INC_SUPPORTED={}" +
                        ")", socketNonEmptySupported, spliceSupported, acceptSupportNoWait,
                        registerIowqWorkersSupported, submitAllSupported, singleIssuerSupported,
                        deferTaskrunSupported, registerBufferRingSupported, registerBufferRingIncSupported);
            }
        }
        UNAVAILABILITY_CAUSE = cause;
        IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED = socketNonEmptySupported;
        IORING_SPLICE_SUPPORTED = spliceSupported;
        IORING_ACCEPT_NO_WAIT_SUPPORTED = acceptSupportNoWait;
        IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED = registerIowqWorkersSupported;
        IORING_SETUP_SUBMIT_ALL_SUPPORTED = submitAllSupported;
        IORING_SETUP_SINGLE_ISSUER_SUPPORTED = singleIssuerSupported;
        IORING_SETUP_DEFER_TASKRUN_SUPPORTED = deferTaskrunSupported;
        IORING_REGISTER_BUFFER_RING_SUPPORTED = registerBufferRingSupported;
        IORING_REGISTER_BUFFER_RING_INC_SUPPORTED = registerBufferRingIncSupported;
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

    static boolean isIOUringSetupSingleIssuerSupported() {
        return IORING_SETUP_SINGLE_ISSUER_SUPPORTED;
    }

    static boolean isIOUringSetupDeferTaskrunSupported() {
        return IORING_SETUP_DEFER_TASKRUN_SUPPORTED;
    }

    public static boolean isRegisterBufferRingSupported() {
        return IORING_REGISTER_BUFFER_RING_SUPPORTED;
    }

    public static boolean isRegisterBufferRingIncSupported() {
        return IORING_REGISTER_BUFFER_RING_INC_SUPPORTED;
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
