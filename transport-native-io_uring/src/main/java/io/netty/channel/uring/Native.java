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

import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Locale;

final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);
    static final int DEFAULT_RING_SIZE = Math.max(64, SystemPropertyUtil.getInt("io.netty.uring.ringSize", 4096));
    static final int DEFAULT_IOSEQ_ASYNC_THRESHOLD =
            Math.max(0, SystemPropertyUtil.getInt("io.netty.uring.iosqeAsyncThreshold", 25));

    static {
        Selector selector = null;
        try {
            // We call Selector.open() as this will under the hood cause IOUtil to be loaded.
            // This is a workaround for a possible classloader deadlock that could happen otherwise:
            //
            // See https://github.com/netty/netty/issues/10187
            selector = Selector.open();
        } catch (IOException ignore) {
            // Just ignore
        }
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            Native.createFile();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException ignore) {
                // Just ignore
            }
        }
        Socket.initialize();
    }
    static final int SOCK_NONBLOCK = NativeStaticallyReferencedJniMethods.sockNonblock();
    static final int SOCK_CLOEXEC = NativeStaticallyReferencedJniMethods.sockCloexec();
    static final short AF_INET = (short) NativeStaticallyReferencedJniMethods.afInet();
    static final short AF_INET6 = (short) NativeStaticallyReferencedJniMethods.afInet6();
    static final int SIZEOF_SOCKADDR_STORAGE = NativeStaticallyReferencedJniMethods.sizeofSockaddrStorage();
    static final int SIZEOF_SOCKADDR_IN = NativeStaticallyReferencedJniMethods.sizeofSockaddrIn();
    static final int SIZEOF_SOCKADDR_IN6 = NativeStaticallyReferencedJniMethods.sizeofSockaddrIn6();
    static final int SOCKADDR_IN_OFFSETOF_SIN_FAMILY =
            NativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinFamily();
    static final int SOCKADDR_IN_OFFSETOF_SIN_PORT = NativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinPort();
    static final int SOCKADDR_IN_OFFSETOF_SIN_ADDR = NativeStaticallyReferencedJniMethods.sockaddrInOffsetofSinAddr();
    static final int IN_ADDRESS_OFFSETOF_S_ADDR = NativeStaticallyReferencedJniMethods.inAddressOffsetofSAddr();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_FAMILY =
            NativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Family();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_PORT =
            NativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Port();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_FLOWINFO =
            NativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Flowinfo();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_ADDR =
            NativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6Addr();
    static final int SOCKADDR_IN6_OFFSETOF_SIN6_SCOPE_ID =
            NativeStaticallyReferencedJniMethods.sockaddrIn6OffsetofSin6ScopeId();
    static final int IN6_ADDRESS_OFFSETOF_S6_ADDR = NativeStaticallyReferencedJniMethods.in6AddressOffsetofS6Addr();
    static final int SIZEOF_SIZE_T = NativeStaticallyReferencedJniMethods.sizeofSizeT();
    static final int SIZEOF_IOVEC = NativeStaticallyReferencedJniMethods.sizeofIovec();
    static final int IOVEC_OFFSETOF_IOV_BASE = NativeStaticallyReferencedJniMethods.iovecOffsetofIovBase();
    static final int IOVEC_OFFSETOF_IOV_LEN = NativeStaticallyReferencedJniMethods.iovecOffsetofIovLen();
    static final int SIZEOF_MSGHDR = NativeStaticallyReferencedJniMethods.sizeofMsghdr();
    static final int MSGHDR_OFFSETOF_MSG_NAME = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgName();
    static final int MSGHDR_OFFSETOF_MSG_NAMELEN = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgNamelen();
    static final int MSGHDR_OFFSETOF_MSG_IOV = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgIov();
    static final int MSGHDR_OFFSETOF_MSG_IOVLEN = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgIovlen();
    static final int MSGHDR_OFFSETOF_MSG_CONTROL = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgControl();
    static final int MSGHDR_OFFSETOF_MSG_CONTROLLEN =
            NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgControllen();
    static final int MSGHDR_OFFSETOF_MSG_FLAGS = NativeStaticallyReferencedJniMethods.msghdrOffsetofMsgFlags();
    static final int POLLIN = NativeStaticallyReferencedJniMethods.pollin();
    static final int POLLOUT = NativeStaticallyReferencedJniMethods.pollout();
    static final int POLLRDHUP = NativeStaticallyReferencedJniMethods.pollrdhup();
    static final int ERRNO_ECANCELED_NEGATIVE = -NativeStaticallyReferencedJniMethods.ecanceled();
    static final int ERRNO_ETIME_NEGATIVE = -NativeStaticallyReferencedJniMethods.etime();
    static final byte IORING_OP_POLL_ADD = NativeStaticallyReferencedJniMethods.ioringOpPollAdd();
    static final byte IORING_OP_TIMEOUT = NativeStaticallyReferencedJniMethods.ioringOpTimeout();
    static final byte IORING_OP_ACCEPT = NativeStaticallyReferencedJniMethods.ioringOpAccept();
    static final byte IORING_OP_READ = NativeStaticallyReferencedJniMethods.ioringOpRead();
    static final byte IORING_OP_WRITE = NativeStaticallyReferencedJniMethods.ioringOpWrite();
    static final byte IORING_OP_POLL_REMOVE = NativeStaticallyReferencedJniMethods.ioringOpPollRemove();
    static final byte IORING_OP_CONNECT = NativeStaticallyReferencedJniMethods.ioringOpConnect();
    static final byte IORING_OP_CLOSE = NativeStaticallyReferencedJniMethods.ioringOpClose();
    static final byte IORING_OP_WRITEV = NativeStaticallyReferencedJniMethods.ioringOpWritev();
    static final byte IORING_OP_SENDMSG = NativeStaticallyReferencedJniMethods.ioringOpSendmsg();
    static final byte IORING_OP_RECVMSG = NativeStaticallyReferencedJniMethods.ioringOpRecvmsg();
    static final int IORING_ENTER_GETEVENTS = NativeStaticallyReferencedJniMethods.ioringEnterGetevents();
    static final int IOSQE_ASYNC = NativeStaticallyReferencedJniMethods.iosqeAsync();

    private static final int[] REQUIRED_IORING_OPS = {
            IORING_OP_POLL_ADD,
            IORING_OP_TIMEOUT,
            IORING_OP_ACCEPT,
            IORING_OP_READ,
            IORING_OP_WRITE,
            IORING_OP_POLL_REMOVE,
            IORING_OP_CONNECT,
            IORING_OP_CLOSE,
            IORING_OP_WRITEV,
            IORING_OP_SENDMSG,
            IORING_OP_RECVMSG
    };

    static RingBuffer createRingBuffer(int ringSize) {
        return createRingBuffer(ringSize, DEFAULT_IOSEQ_ASYNC_THRESHOLD);
    }

    static RingBuffer createRingBuffer(int ringSize, int iosqeAsyncThreshold) {
        long[][] values = ioUringSetup(ringSize);
        assert values.length == 2;
        long[] submissionQueueArgs = values[0];
        assert submissionQueueArgs.length == 11;
        IOUringSubmissionQueue submissionQueue = new IOUringSubmissionQueue(
                submissionQueueArgs[0],
                submissionQueueArgs[1],
                submissionQueueArgs[2],
                submissionQueueArgs[3],
                submissionQueueArgs[4],
                submissionQueueArgs[5],
                submissionQueueArgs[6],
                submissionQueueArgs[7],
                (int) submissionQueueArgs[8],
                submissionQueueArgs[9],
                (int) submissionQueueArgs[10],
                iosqeAsyncThreshold);
        long[] completionQueueArgs = values[1];
        assert completionQueueArgs.length == 9;
        IOUringCompletionQueue completionQueue = new IOUringCompletionQueue(
                completionQueueArgs[0],
                completionQueueArgs[1],
                completionQueueArgs[2],
                completionQueueArgs[3],
                completionQueueArgs[4],
                completionQueueArgs[5],
                (int) completionQueueArgs[6],
                completionQueueArgs[7],
                (int) completionQueueArgs[8]);
        return new RingBuffer(submissionQueue, completionQueue);
    }

    static RingBuffer createRingBuffer() {
        return createRingBuffer(DEFAULT_RING_SIZE, DEFAULT_IOSEQ_ASYNC_THRESHOLD);
    }

    static void checkAllIOSupported(int ringFd) {
        if (!ioUringProbe(ringFd, REQUIRED_IORING_OPS)) {
            throw new UnsupportedOperationException("Not all operations are supported: "
                    + Arrays.toString(REQUIRED_IORING_OPS));
        }
    }

    private static native boolean ioUringProbe(int ringFd, int[] ios);
    private static native long[][] ioUringSetup(int entries);

    public static native int ioUringEnter(int ringFd, int toSubmit, int minComplete, int flags);

    public static native void eventFdWrite(int fd, long value);

    public static FileDescriptor newBlockingEventFd() {
        return new FileDescriptor(blockingEventFd());
    }

    public static native void ioUringExit(long submissionQueueArrayAddress, int submissionQueueRingEntries,
                                          long submissionQueueRingAddress, int submissionQueueRingSize,
                                          long completionQueueRingAddress, int completionQueueRingSize,
                                          int ringFd);

    private static native int blockingEventFd();

    // for testing(it is only temporary)
    public static native int createFile();

    private Native() {
        // utility
    }

    // From io_uring native library
    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs().toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        String staticLibName = "netty_transport_native_io_uring";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.info("Failed to load io_uring");
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }
}
