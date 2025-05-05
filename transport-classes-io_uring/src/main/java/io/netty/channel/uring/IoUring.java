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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.Limits;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class IoUring {

    private static final Throwable UNAVAILABILITY_CAUSE;
    private static final boolean IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED;
    private static final boolean IORING_SPLICE_SUPPORTED;
    private static final boolean IORING_ACCEPT_NO_WAIT_SUPPORTED;
    private static final boolean IORING_ACCEPT_MULTISHOT_SUPPORTED;
    private static final boolean IORING_RECV_MULTISHOT_SUPPORTED;
    private static final boolean IORING_RECVSEND_BUNDLE_SUPPORTED;
    private static final boolean IORING_POLL_ADD_MULTISHOT_SUPPORTED;
    private static final boolean IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED;
    private static final boolean IORING_SETUP_SUBMIT_ALL_SUPPORTED;
    private static final boolean IORING_SETUP_CQ_SIZE_SUPPORTED;
    private static final boolean IORING_SETUP_SINGLE_ISSUER_SUPPORTED;
    private static final boolean IORING_SETUP_DEFER_TASKRUN_SUPPORTED;
    private static final boolean IORING_REGISTER_BUFFER_RING_SUPPORTED;
    private static final boolean IORING_REGISTER_BUFFER_RING_INC_SUPPORTED;
    private static final boolean IORING_ACCEPT_MULTISHOT_ENABLED;
    private static final boolean IORING_RECV_MULTISHOT_ENABLED;
    private static final boolean IORING_RECVSEND_BUNDLE_ENABLED;
    private static final boolean IORING_POLL_ADD_MULTISHOT_ENABLED;

    static final int NUM_ELEMENTS_IOVEC;

    private static final InternalLogger logger;

    static {
        logger = InternalLoggerFactory.getInstance(IoUring.class);
        Throwable cause = null;
        boolean socketNonEmptySupported = false;
        boolean spliceSupported = false;
        boolean acceptSupportNoWait = false;
        boolean acceptMultishotSupported = false;
        boolean recvsendBundleSupported = false;
        boolean recvMultishotSupported = false;
        boolean pollAddMultishotSupported = false;
        boolean registerIowqWorkersSupported = false;
        boolean submitAllSupported = false;
        boolean setUpCqSizeSupported = false;
        boolean singleIssuerSupported = false;
        boolean deferTaskrunSupported = false;
        boolean registerBufferRingSupported = false;
        boolean registerBufferRingIncSupported = false;
        int numElementsIoVec = 10;

        String kernelVersion = "[unknown]";
        try {
            if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
                cause = new UnsupportedOperationException(
                        "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
            } else {
                kernelVersion = Native.kernelVersion();
                Native.checkKernelVersion(kernelVersion);
                if (PlatformDependent.javaVersion() >= 9) {
                    RingBuffer ringBuffer = null;
                    try {
                        ringBuffer = Native.createRingBuffer(1, 0);
                        if ((ringBuffer.features() & Native.IORING_FEAT_SUBMIT_STABLE) == 0) {
                            // This should only happen on kernels < 5.4 which we don't support anyway.
                            throw new UnsupportedOperationException("IORING_FEAT_SUBMIT_STABLE not supported!");
                        }
                        // IOV_MAX should be 1024 and an IOV is 16 bytes which means that by default we reserve around
                        // 160kb.
                        numElementsIoVec = SystemPropertyUtil.getInt(
                                "io.netty.iouring.numElementsIoVec", 10 *  Limits.IOV_MAX);
                        Native.checkAllIOSupported(ringBuffer.fd());
                        socketNonEmptySupported = Native.isCqeFSockNonEmptySupported(ringBuffer.fd());
                        spliceSupported = Native.isSpliceSupported(ringBuffer.fd());
                        recvsendBundleSupported = (ringBuffer.features() & Native.IORING_FEAT_RECVSEND_BUNDLE) != 0;
                        // IORING_FEAT_RECVSEND_BUNDLE was added in the same release.
                        acceptSupportNoWait = recvsendBundleSupported;
                        // Explicit disable recvsend bundles as there seems to be a bug which cause
                        // and AssertionError which leads to the CI running out of memory.
                        // We will enable this again once we found the bug and fixed it.
                        //
                        // TODO: Remove once fixed.
                        recvsendBundleSupported = false;
                        acceptMultishotSupported = Native.isAcceptMultishotSupported(ringBuffer.fd());
                        recvMultishotSupported = Native.isRecvMultishotSupported();
                        pollAddMultishotSupported = Native.isPollAddMultiShotSupported(ringBuffer.fd());
                        registerIowqWorkersSupported = Native.isRegisterIoWqWorkerSupported(ringBuffer.fd());
                        submitAllSupported = Native.ioUringSetupSupportsFlags(Native.IORING_SETUP_SUBMIT_ALL);
                        setUpCqSizeSupported = Native.ioUringSetupSupportsFlags(Native.IORING_SETUP_CQSIZE);
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
                    cause = new UnsupportedOperationException("Java 9+ is required");
                }
            }
        } catch (Throwable t) {
            cause = t;
        }
        if (cause != null) {
            if (logger.isTraceEnabled()) {
                logger.debug("IoUring support is not available using kernel {}", kernelVersion, cause);
            } else if (logger.isDebugEnabled()) {
                logger.debug("IoUring support is not available using kernel {}: {}", kernelVersion, cause.getMessage());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("IoUring support is available using kernel {} (" +
                        "CQE_F_SOCK_NONEMPTY_SUPPORTED={}, " +
                        "SPLICE_SUPPORTED={}, " +
                        "ACCEPT_NO_WAIT_SUPPORTED={}, " +
                        "ACCEPT_MULTISHOT_SUPPORTED={}, " +
                        "POLL_ADD_MULTISHOT_SUPPORTED={} " +
                        "RECV_MULTISHOT_SUPPORTED={}, " +
                        "IORING_RECVSEND_BUNDLE_SUPPORTED={}, " +
                        "REGISTER_IOWQ_MAX_WORKERS_SUPPORTED={}, " +
                        "SETUP_SUBMIT_ALL_SUPPORTED={}, " +
                        "SETUP_SINGLE_ISSUER_SUPPORTED={}, " +
                        "SETUP_DEFER_TASKRUN_SUPPORTED={}, " +
                        "REGISTER_BUFFER_RING_SUPPORTED={}, " +
                        "REGISTER_BUFFER_RING_INC_SUPPORTED={}" +
                        ")", kernelVersion, socketNonEmptySupported, spliceSupported, acceptSupportNoWait,
                        acceptMultishotSupported, pollAddMultishotSupported, recvMultishotSupported,
                        recvsendBundleSupported, registerIowqWorkersSupported, submitAllSupported,
                        singleIssuerSupported, deferTaskrunSupported,
                        registerBufferRingSupported, registerBufferRingIncSupported);
            }
        }
        UNAVAILABILITY_CAUSE = cause;
        IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED = socketNonEmptySupported;
        IORING_SPLICE_SUPPORTED = spliceSupported;
        IORING_ACCEPT_NO_WAIT_SUPPORTED = acceptSupportNoWait;
        IORING_ACCEPT_MULTISHOT_SUPPORTED = acceptMultishotSupported;
        IORING_RECV_MULTISHOT_SUPPORTED = recvMultishotSupported;
        IORING_RECVSEND_BUNDLE_SUPPORTED = recvsendBundleSupported;
        IORING_POLL_ADD_MULTISHOT_SUPPORTED = pollAddMultishotSupported;
        IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED = registerIowqWorkersSupported;
        IORING_SETUP_SUBMIT_ALL_SUPPORTED = submitAllSupported;
        IORING_SETUP_CQ_SIZE_SUPPORTED = setUpCqSizeSupported;
        IORING_SETUP_SINGLE_ISSUER_SUPPORTED = singleIssuerSupported;
        IORING_SETUP_DEFER_TASKRUN_SUPPORTED = deferTaskrunSupported;
        IORING_REGISTER_BUFFER_RING_SUPPORTED = registerBufferRingSupported;
        IORING_REGISTER_BUFFER_RING_INC_SUPPORTED = registerBufferRingIncSupported;

        IORING_ACCEPT_MULTISHOT_ENABLED = IORING_ACCEPT_MULTISHOT_SUPPORTED && SystemPropertyUtil.getBoolean(
                "io.netty.iouring.acceptMultiShotEnabled", true);
        IORING_RECV_MULTISHOT_ENABLED = IORING_RECV_MULTISHOT_SUPPORTED && SystemPropertyUtil.getBoolean(
                "io.netty.iouring.recvMultiShotEnabled", true);
        IORING_RECVSEND_BUNDLE_ENABLED = IORING_RECVSEND_BUNDLE_SUPPORTED && SystemPropertyUtil.getBoolean(
                "io.netty.iouring.recvsendBundleEnabled", true);
        IORING_POLL_ADD_MULTISHOT_ENABLED = IORING_POLL_ADD_MULTISHOT_SUPPORTED && SystemPropertyUtil.getBoolean(
               "io.netty.iouring.pollAddMultishotEnabled", true);
        NUM_ELEMENTS_IOVEC = numElementsIoVec;
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

    static boolean isCqeFSockNonEmptySupported() {
        return IORING_CQE_F_SOCK_NONEMPTY_SUPPORTED;
    }

    /**
     * Returns if SPLICE is supported or not.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isSpliceSupported() {
        return IORING_SPLICE_SUPPORTED;
    }

    static boolean isAcceptNoWaitSupported() {
        return IORING_ACCEPT_NO_WAIT_SUPPORTED;
    }

    static boolean isAcceptMultishotSupported() {
        return IORING_ACCEPT_MULTISHOT_SUPPORTED;
    }

    static boolean isRecvMultishotSupported() {
        return IORING_RECV_MULTISHOT_SUPPORTED;
    }

    static boolean isRecvsendBundleSupported() {
        return IORING_RECVSEND_BUNDLE_SUPPORTED;
    }

    static boolean isPollAddMultishotSupported() {
        return IORING_POLL_ADD_MULTISHOT_SUPPORTED;
    }

    static boolean isRegisterIowqMaxWorkersSupported() {
        return IORING_REGISTER_IOWQ_MAX_WORKERS_SUPPORTED;
    }

    static boolean isSetupCqeSizeSupported() {
        return IORING_SETUP_CQ_SIZE_SUPPORTED;
    }

    static boolean isSetupSubmitAllSupported() {
        return IORING_SETUP_SUBMIT_ALL_SUPPORTED;
    }

    static boolean isSetupSingleIssuerSupported() {
        return IORING_SETUP_SINGLE_ISSUER_SUPPORTED;
    }

    static boolean isSetupDeferTaskrunSupported() {
        return IORING_SETUP_DEFER_TASKRUN_SUPPORTED;
    }

    /**
     * Returns if it is supported to use a buffer ring.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isRegisterBufferRingSupported() {
        return IORING_REGISTER_BUFFER_RING_SUPPORTED;
    }

    /**
     * Returns if it is supported to use an incremental buffer ring.
     *
     * @return {@code true} if supported, {@code false} otherwise.
     */
    public static boolean isRegisterBufferRingIncSupported() {
        return IORING_REGISTER_BUFFER_RING_INC_SUPPORTED;
    }

    /**
     * Returns if multi-shot ACCEPT is used or not.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public static boolean isAcceptMultishotEnabled() {
        return IORING_ACCEPT_MULTISHOT_ENABLED;
    }

    /**
     * Returns if multi-shot RECV is used or not.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public static boolean isRecvMultishotEnabled() {
        return IORING_RECV_MULTISHOT_ENABLED;
    }

    /**
     * Returns if RECVSEND bundles are used or not.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public static boolean isRecvsendBundleEnabled() {
        return IORING_RECVSEND_BUNDLE_ENABLED;
    }

    /**
     * Returns if multi-shot POLL_ADD is used or not.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public static boolean isPollAddMultishotEnabled() {
        return IORING_POLL_ADD_MULTISHOT_ENABLED;
    }

    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    static long memoryAddress(ByteBuf buffer) {
        if (buffer.hasMemoryAddress()) {
            return buffer.memoryAddress();
        }
        return Buffer.memoryAddress(buffer.internalNioBuffer(0, buffer.capacity()));
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private IoUring() {
    }
}
