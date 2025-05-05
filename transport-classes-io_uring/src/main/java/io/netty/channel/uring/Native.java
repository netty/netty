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

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.Buffer;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Unix;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);
    static final int DEFAULT_RING_SIZE = Math.max(64, SystemPropertyUtil.getInt("io.netty.iouring.ringSize", 4096));

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

        // Preload all classes that will be used in the OnLoad(...) function of JNI to eliminate the possiblity of a
        // class-loader deadlock. This is a workaround for https://github.com/netty/netty/issues/11209.

        // This needs to match all the classes that are loaded via NETTY_JNI_UTIL_LOAD_CLASS or looked up via
        // NETTY_JNI_UTIL_FIND_CLASS.
        ClassInitializerUtil.tryLoadClasses(
                Native.class,
                // netty_io_uring_linuxsocket
                PeerCredentials.class, java.io.FileDescriptor.class
        );

        File tmpDir = PlatformDependent.tmpdir();
        Path tmpFile = tmpDir.toPath().resolve("netty_io_uring.tmp");
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            Native.createFile(tmpFile.toString());
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        } finally {
            tmpFile.toFile().delete();
            try {
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException ignore) {
                // Just ignore
            }
        }
        Unix.registerInternal(Native::registerUnix);
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
    static final int CMSG_SPACE = NativeStaticallyReferencedJniMethods.cmsgSpace();
    static final int CMSG_LEN = NativeStaticallyReferencedJniMethods.cmsgLen();
    static final int CMSG_OFFSETOF_CMSG_LEN = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLen();
    static final int CMSG_OFFSETOF_CMSG_LEVEL = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgLevel();
    static final int CMSG_OFFSETOF_CMSG_TYPE = NativeStaticallyReferencedJniMethods.cmsghdrOffsetofCmsgType();

    static final int IO_URING_BUFFER_RING_TAIL = NativeStaticallyReferencedJniMethods.ioUringBufferRingOffsetTail();

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
    static final int ERRNO_NOBUFS_NEGATIVE = -NativeStaticallyReferencedJniMethods.enobufs();

    static final int PAGE_SIZE = NativeStaticallyReferencedJniMethods.pageSize();

    static final int SIZEOF_IOURING_BUF = NativeStaticallyReferencedJniMethods.sizeofIoUringBuf();
    static final int IOURING_BUFFER_OFFSETOF_ADDR = NativeStaticallyReferencedJniMethods.ioUringBufferOffsetAddr();
    static final int IOURING_BUFFER_OFFSETOF_LEN = NativeStaticallyReferencedJniMethods.ioUringBufferOffsetLen();
    static final int IOURING_BUFFER_OFFSETOF_BID = NativeStaticallyReferencedJniMethods.ioUringBufferOffsetBid();

    // These constants must be defined to have the same numeric value as their corresponding
    // ordinal in the enum defined in the io_uring.h header file.
    // DO NOT CHANGE THESE VALUES!
    static final byte IORING_OP_NOP = 0; // Specified by IORING_OP_NOP in io_uring.h
    static final byte IORING_OP_READV = 1; // Specified by IORING_OP_READV in io_uring.h
    static final byte IORING_OP_WRITEV = 2; // Specified by IORING_OP_WRITEV in io_uring.h
    static final byte IORING_OP_FSYNC = 3; // Specified by IORING_OP_FSYNC in io_uring.h
    static final byte IORING_OP_READ_FIXED = 4; // Specified by IORING_OP_READ_FIXED in io_uring.h
    static final byte IORING_OP_WRITE_FIXED = 5; // Specified by IORING_OP_WRITE_FIXED in io_uring.h
    static final byte IORING_OP_POLL_ADD = 6; // Specified by IORING_OP_POLL_ADD in io_uring.h
    static final byte IORING_OP_POLL_REMOVE = 7; // Specified by IORING_OP_POLL_REMOVE in io_uring.h
    static final byte IORING_OP_SYNC_FILE_RANGE = 8; // Specified by IORING_OP_SYNC_FILE_RANGE in io_uring.h
    static final byte IORING_OP_SENDMSG = 9; // Specified by IORING_OP_SENDMSG in io_uring.h
    static final byte IORING_OP_RECVMSG = 10; // Specified by IORING_OP_RECVMSG in io_uring.h
    static final byte IORING_OP_TIMEOUT = 11; // Specified by IORING_OP_TIMEOUT in io_uring.h
    static final byte IORING_OP_TIMEOUT_REMOVE = 12; // Specified by IORING_OP_TIMEOUT_REMOVE in io_uring.h
    static final byte IORING_OP_ACCEPT = 13; // Specified by IORING_OP_ACCEPT in io_uring.h
    static final byte IORING_OP_ASYNC_CANCEL = 14; // Specified by IORING_OP_ASYNC_CANCEL in io_uring.h
    static final byte IORING_OP_LINK_TIMEOUT = 15; // Specified by IORING_OP_LINK_TIMEOUT in io_uring.h
    static final byte IORING_OP_CONNECT = 16; // Specified by IORING_OP_CONNECT in io_uring.h
    static final byte IORING_OP_FALLOCATE = 17; // Specified by IORING_OP_FALLOCATE in io_uring.h
    static final byte IORING_OP_OPENAT = 18; // Specified by IORING_OP_OPENAT in io_uring.h
    static final byte IORING_OP_CLOSE = 19; // Specified by IORING_OP_CLOSE in io_uring.h
    static final byte IORING_OP_FILES_UPDATE = 20; // Specified by IORING_OP_FILES_UPDATE in io_uring.h
    static final byte IORING_OP_STATX = 21; // Specified by IORING_OP_STATX in io_uring.h
    static final byte IORING_OP_READ = 22; // Specified by IORING_OP_READ in io_uring.h
    static final byte IORING_OP_WRITE = 23; // Specified by IORING_OP_WRITE in io_uring.h
    static final byte IORING_OP_FADVISE = 24; // Specified by IORING_OP_FADVISE in io_uring.h
    static final byte IORING_OP_MADVISE = 25; // Specified by IORING_OP_MADVISE in io_uring.h
    static final byte IORING_OP_SEND = 26; // Specified by IORING_OP_SEND in io_uring.h
    static final byte IORING_OP_RECV = 27; // Specified by IORING_OP_RECV in io_uring.h
    static final byte IORING_OP_OPENAT2 = 28; // Specified by IORING_OP_OPENAT2 in io_uring.h
    static final byte IORING_OP_EPOLL_CTL = 29; // Specified by IORING_OP_EPOLL_CTL in io_uring.h
    static final byte IORING_OP_SPLICE = 30; // Specified by IORING_OP_SPLICE in io_uring.h
    static final byte IORING_OP_PROVIDE_BUFFERS = 31; // Specified by IORING_OP_PROVIDE_BUFFERS in io_uring.h
    static final byte IORING_OP_REMOVE_BUFFERS = 32; // Specified by IORING_OP_REMOVE_BUFFERS in io_uring.h
    static final byte IORING_OP_TEE = 33; // Specified by IORING_OP_TEE in io_uring.h
    static final byte IORING_OP_SHUTDOWN = 34; // Specified by IORING_OP_SHUTDOWN in io_uring.h
    static final byte IORING_OP_RENAMEAT = 35; // Specified by IORING_OP_RENAMEAT in io_uring.h
    static final byte IORING_OP_UNLINKAT = 36; // Specified by IORING_OP_UNLINKAT in io_uring.h
    static final byte IORING_OP_MKDIRAT = 37; // Specified by IORING_OP_MKDIRAT in io_uring.h
    static final byte IORING_OP_SYMLINKAT = 38; // Specified by IORING_OP_SYMLINKAT in io_uring.h
    static final byte IORING_OP_LINKAT = 39; // Specified by IORING_OP_LINKAT in io_uring.h
    static final byte IORING_OP_MSG_RING = 40;
    static final byte IORING_OP_FSETXATTR = 41;
    static final byte IORING_OP_SETXATTR = 42;
    static final byte IORING_OP_FGETXATTR = 43;
    static final byte IORING_OP_GETXATTR = 44;
    static final byte IORING_OP_SOCKET = 45;
    static final byte IORING_OP_URING_CMD = 46;
    static final byte IORING_OP_SEND_ZC = 47;
    static final byte IORING_OP_SENDMSG_ZC = 48;
    static final byte IORING_OP_READ_MULTISHOT = 49;
    static final byte IORING_OP_WAITID = 50;
    static final byte IORING_OP_FUTEX_WAIT = 51;
    static final byte IORING_OP_FUTEX_WAKE = 52;
    static final byte IORING_OP_FUTEX_WAITV = 53;
    static final byte IORING_OP_FIXED_FD_INSTALL = 54;
    static final byte IORING_OP_FTRUNCATE = 55;
    static final byte IORING_OP_BIND = 56;
    static final byte IORING_CQE_F_BUFFER = 1 << 0;
    static final byte IORING_CQE_F_MORE = 1 << 1;
    static final byte IORING_CQE_F_SOCK_NONEMPTY = 1 << 2;
    static final byte IORING_CQE_F_BUF_MORE = 1 << 4;

    static final int IORING_SETUP_CQSIZE = 1 << 3;
    static final int IORING_SETUP_CLAMP = 1 << 4;

    static final int IORING_SETUP_R_DISABLED = 1 << 6;
    static final int IORING_SETUP_SUBMIT_ALL = 1 << 7;
    static final int IORING_SETUP_SINGLE_ISSUER = 1 << 12;
    static final int IORING_SETUP_DEFER_TASKRUN = 1 << 13;
    static final int IORING_CQE_BUFFER_SHIFT = 16;

    static final short IORING_POLL_ADD_MULTI = 1 << 0;

    static final short IORING_RECVSEND_POLL_FIRST = 1 << 0;
    static final short IORING_RECVSEND_BUNDLE = 1 << 4;
    static final short IORING_RECV_MULTISHOT = 1 << 1;

    static final short IORING_ACCEPT_MULTISHOT = 1 << 0;
    static final short IORING_ACCEPT_DONTWAIT = 1 << 1;
    static final short IORING_ACCEPT_POLL_FIRST = 1 << 2;

    static final int IORING_FEAT_SUBMIT_STABLE = 1 << 2;
    static final int IORING_FEAT_RECVSEND_BUNDLE = 1 << 14;

    static final int SPLICE_F_MOVE = 1;

    static final int IOU_PBUF_RING_INC = 2;
    static String opToStr(byte op) {
        switch (op) {
            case IORING_OP_NOP: return "NOP";
            case IORING_OP_READV: return "READV";
            case IORING_OP_WRITEV: return "WRITEV";
            case IORING_OP_FSYNC: return "FSYNC";
            case IORING_OP_READ_FIXED: return "READ_FIXED";
            case IORING_OP_WRITE_FIXED: return "WRITE_FIXED";
            case IORING_OP_POLL_ADD: return "POLL_ADD";
            case IORING_OP_POLL_REMOVE: return "POLL_REMOVE";
            case IORING_OP_SYNC_FILE_RANGE: return "SYNC_FILE_RANGE";
            case IORING_OP_SENDMSG: return "SENDMSG";
            case IORING_OP_RECVMSG: return "RECVMSG";
            case IORING_OP_TIMEOUT: return "TIMEOUT";
            case IORING_OP_TIMEOUT_REMOVE: return "TIMEOUT_REMOVE";
            case IORING_OP_ACCEPT: return "ACCEPT";
            case IORING_OP_ASYNC_CANCEL: return "ASYNC_CANCEL";
            case IORING_OP_LINK_TIMEOUT: return "LINK_TIMEOUT";
            case IORING_OP_CONNECT: return "CONNECT";
            case IORING_OP_FALLOCATE: return "FALLOCATE";
            case IORING_OP_OPENAT: return "OPENAT";
            case IORING_OP_CLOSE: return "CLOSE";
            case IORING_OP_FILES_UPDATE: return "FILES_UPDATE";
            case IORING_OP_STATX: return "STATX";
            case IORING_OP_READ: return "READ";
            case IORING_OP_WRITE: return "WRITE";
            case IORING_OP_FADVISE: return "FADVISE";
            case IORING_OP_MADVISE: return "MADVISE";
            case IORING_OP_SEND: return "SEND";
            case IORING_OP_RECV: return "RECV";
            case IORING_OP_OPENAT2: return "OPENAT2";
            case IORING_OP_EPOLL_CTL: return "EPOLL_CTL";
            case IORING_OP_SPLICE: return "SPLICE";
            case IORING_OP_PROVIDE_BUFFERS: return "PROVIDE_BUFFERS";
            case IORING_OP_REMOVE_BUFFERS: return "REMOVE_BUFFERS";
            case IORING_OP_TEE: return "TEE";
            case IORING_OP_SHUTDOWN: return "SHUTDOWN";
            case IORING_OP_RENAMEAT: return "RENAMEAT";
            case IORING_OP_UNLINKAT: return "UNLINKAT";
            case IORING_OP_MKDIRAT: return "MKDIRAT";
            case IORING_OP_SYMLINKAT: return "SYMLINKAT";
            case IORING_OP_LINKAT: return "LINKAT";
            default: return "[OP CODE " + op + ']';
        }
    }

    static final int IORING_ENTER_GETEVENTS = NativeStaticallyReferencedJniMethods.ioringEnterGetevents();
    static final int IORING_ENTER_REGISTERED_RING = 1 << 4;
    static final int IOSQE_ASYNC = NativeStaticallyReferencedJniMethods.iosqeAsync();
    static final int IOSQE_LINK = NativeStaticallyReferencedJniMethods.iosqeLink();
    static final int IOSQE_IO_DRAIN = NativeStaticallyReferencedJniMethods.iosqeDrain();
    static final int IOSQE_BUFFER_SELECT = NativeStaticallyReferencedJniMethods.iosqeBufferSelect();
    static final int IOSQE_CQE_SKIP_SUCCESS = 1 << 6;
    static final int MSG_DONTWAIT = NativeStaticallyReferencedJniMethods.msgDontwait();
    static final int MSG_FASTOPEN = NativeStaticallyReferencedJniMethods.msgFastopen();
    static final int SOL_UDP = NativeStaticallyReferencedJniMethods.solUdp();
    static final int UDP_SEGMENT = NativeStaticallyReferencedJniMethods.udpSegment();
    private static final int TFO_ENABLED_CLIENT_MASK = 0x1;
    private static final int TFO_ENABLED_SERVER_MASK = 0x2;
    private static final int TCP_FASTOPEN_MODE = NativeStaticallyReferencedJniMethods.tcpFastopenMode();
    /**
     * <a href ="https://www.kernel.org/doc/Documentation/networking/ip-sysctl.txt">tcp_fastopen</a> client mode enabled
     * state.
     */
    static final boolean IS_SUPPORTING_TCP_FASTOPEN_CLIENT =
            (TCP_FASTOPEN_MODE & TFO_ENABLED_CLIENT_MASK) == TFO_ENABLED_CLIENT_MASK;
    /**
     * <a href ="https://www.kernel.org/doc/Documentation/networking/ip-sysctl.txt">tcp_fastopen</a> server mode enabled
     * state.
     */
    static final boolean IS_SUPPORTING_TCP_FASTOPEN_SERVER =
            (TCP_FASTOPEN_MODE & TFO_ENABLED_SERVER_MASK) == TFO_ENABLED_SERVER_MASK;

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
            IORING_OP_RECVMSG,
            IORING_OP_ASYNC_CANCEL,
            IORING_OP_RECV,
            IORING_OP_NOP,
            IORING_OP_SHUTDOWN,
            IORING_OP_SEND
    };

    static int setupFlags() {
        int flags = Native.IORING_SETUP_R_DISABLED | Native.IORING_SETUP_CLAMP;
        if (IoUring.isSetupSubmitAllSupported()) {
            flags |= Native.IORING_SETUP_SUBMIT_ALL;
        }

        // See https://github.com/axboe/liburing/wiki/io_uring-and-networking-in-2023#task-work
        if (IoUring.isSetupSingleIssuerSupported()) {
            flags |= Native.IORING_SETUP_SINGLE_ISSUER;
        }
        if (IoUring.isSetupDeferTaskrunSupported()) {
            flags |= Native.IORING_SETUP_DEFER_TASKRUN;
        }
        return flags;
    }

    static RingBuffer createRingBuffer(int ringSize, int setupFlags) {
        return createRingBuffer(ringSize, ringSize * 2, setupFlags);
    }

    static RingBuffer createRingBuffer(int ringSize, int cqeSize, int setupFlags) {
        ObjectUtil.checkPositive(ringSize, "ringSize");
        ObjectUtil.checkPositive(cqeSize, "cqeSize");
        long[] values = ioUringSetup(ringSize, cqeSize, setupFlags);
        assert values.length == 18;
        long cqkhead = values[0];
        long cqktail = values[1];
        int cqringMask = (int) values[2];
        int cqringEntries = (int) values[3];
        long cqArrayAddress = values[4];
        int cqringSize = (int) values[5];
        long cqringAddress = values[6];
        int cqringFd = (int) values[7];
        int cqringCapacity = (int) values[8];
        CompletionQueue completionQueue = new CompletionQueue(
                Buffer.wrapMemoryAddressWithNativeOrder(cqkhead, Integer.BYTES),
                Buffer.wrapMemoryAddressWithNativeOrder(cqktail, Integer.BYTES),
                cqringMask,
                cqringEntries,
                Buffer.wrapMemoryAddressWithNativeOrder(cqArrayAddress, cqringEntries * CompletionQueue.CQE_SIZE),
                cqringSize,
                cqringAddress,
                cqringFd,
                cqringCapacity);

        long sqkhead = values[9];
        long sqktail = values[10];
        int sqringMask = (int) values[11];
        int sqringEntries = (int) values[12];
        long sqArrayAddress = values[13];
        int sqringSize = (int) values[14];
        long sqringAddress = values[15];
        int sqringFd = (int) values[16];
        SubmissionQueue submissionQueue = new SubmissionQueue(
                Buffer.wrapMemoryAddressWithNativeOrder(sqkhead, Integer.BYTES),
                Buffer.wrapMemoryAddressWithNativeOrder(sqktail, Integer.BYTES),
                sqringMask,
                sqringEntries,
                Buffer.wrapMemoryAddressWithNativeOrder(sqArrayAddress, sqringEntries * SubmissionQueue.SQE_SIZE),
                sqringSize,
                sqringAddress,
                sqringFd);
        return new RingBuffer(submissionQueue, completionQueue, (int) values[17]);
    }

    static void checkAllIOSupported(int ringFd) {
        if (!ioUringProbe(ringFd, REQUIRED_IORING_OPS)) {
            throw new UnsupportedOperationException("Not all operations are supported: "
                    + Arrays.toString(REQUIRED_IORING_OPS));
        }
    }

    static boolean isRecvMultishotSupported() {
        // Added in the same release as IORING_SETUP_SINGLE_ISSUER.
        return Native.ioUringSetupSupportsFlags(Native.IORING_SETUP_SINGLE_ISSUER);
    }

    static boolean isAcceptMultishotSupported(int ringFd) {
        // IORING_OP_SOCKET was added in the same release (5.19);
        return ioUringProbe(ringFd, new int[] { Native.IORING_OP_SOCKET });
    }

    static boolean isCqeFSockNonEmptySupported(int ringFd) {
        // IORING_OP_SOCKET was added in the same release (5.19);
        return ioUringProbe(ringFd, new int[] { Native.IORING_OP_SOCKET });
    }

    static boolean isSpliceSupported(int ringFd) {
        // IORING_OP_SPLICE Available since 5.7
        return ioUringProbe(ringFd, new int[] { Native.IORING_OP_SPLICE });
    }

    static boolean isPollAddMultiShotSupported(int ringfd) {
        // Was added in the same release and we also need this feature to correctly handle edge-triggered mode.
        return isCqeFSockNonEmptySupported(ringfd);
    }

    /**
     * check current kernel version whether support io_uring_register_io_wq_worker
     * Available since 5.15.
     * @return true if support io_uring_register_io_wq_worker
     */
    static boolean isRegisterIoWqWorkerSupported(int ringFd) {
        // See https://github.com/torvalds/linux/blob/v5.5/fs/io_uring.c#L5488C10-L5488C16
        int result = ioUringRegisterIoWqMaxWorkers(ringFd, 0, 0);
        if (result >= 0) {
            return true;
        }
        // This is not supported and so will return -EINVAL
        return false;
    }

    static boolean isRegisterBufferRingSupported(int ringFd, int flags) {
        int entries = 2;
        short bgid = 1;
        long result = ioUringRegisterBufRing(ringFd, entries, bgid, flags);
        if (result >= 0) {
            ioUringUnRegisterBufRing(ringFd, result, entries, bgid);
            return true;
        }
        // This is not supported and so will return -EINVAL
        return false;
    }

    static void checkKernelVersion(String kernelVersion) {
        boolean enforceKernelVersion = SystemPropertyUtil.getBoolean(
                "io.netty.transport.iouring.enforceKernelVersion", true);
        boolean kernelSupported = checkKernelVersion(kernelVersion, 5, 9);
        if (!kernelSupported) {
            if (enforceKernelVersion) {
                throw new UnsupportedOperationException(
                        "you need at least kernel version 5.9, current kernel version: " + kernelVersion);
            } else {
                logger.debug("Detected kernel " + kernelVersion + " does not match minimum version of 5.9, " +
                        "trying to use io_uring anyway");
            }
        }
    }

    private static boolean checkKernelVersion(String kernelVersion, int major, int minor) {
        String[] versionComponents = kernelVersion.split("\\.");
        if (versionComponents.length < 3) {
            return false;
        }
        int nativeMajor;
        try {
            nativeMajor = Integer.parseInt(versionComponents[0]);
        } catch (NumberFormatException e) {
            return false;
        }

        if (nativeMajor < major) {
            return false;
        }

        if (nativeMajor > major) {
            return true;
        }

        int nativeMinor;
        try {
            nativeMinor = Integer.parseInt(versionComponents[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        return nativeMinor >= minor;
    }

    static native boolean ioUringSetupSupportsFlags(int setupFlags);
    private static native boolean ioUringProbe(int ringFd, int[] ios);
    private static native long[] ioUringSetup(int entries, int cqeSize, int setupFlags);

    static native int ioUringRegisterIoWqMaxWorkers(int ringFd, int maxBoundedValue, int maxUnboundedValue);
    static native int ioUringRegisterEnableRings(int ringFd);
    static native int ioUringRegisterRingFds(int ringFds);

    static native long ioUringRegisterBufRing(int ringFd, int entries, short bufferGroup, int flags);
    static native int ioUringUnRegisterBufRing(int ringFd, long ioUringBufRingAddr, int entries, short bufferGroupId);
    static native int ioUringBufRingSize(int entries);
    static native int ioUringEnter(int ringFd, int toSubmit, int minComplete, int flags);

    static native void eventFdWrite(int fd, long value);

    static int getFd(DefaultFileRegion fileChannel) {
        return getFd0(fileChannel);
    }

    private static native int getFd0(Object fileChannel);

    static FileDescriptor newBlockingEventFd() {
        return new FileDescriptor(blockingEventFd());
    }

    static native void ioUringExit(long submissionQueueArrayAddress, int submissionQueueRingEntries,
                                          long submissionQueueRingAddress, int submissionQueueRingSize,
                                          long completionQueueRingAddress, int completionQueueRingSize,
                                          int ringFd, int enterRingFd);

    private static native int blockingEventFd();

    // for testing only!
    static native int createFile(String name);

    private static native int registerUnix();

    static native long cmsghdrData(long hdrAddr);

    static native String kernelVersion();

    private Native() {
        // utility
    }

    // From io_uring native library
    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs().toLowerCase(Locale.ROOT).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        String staticLibName = "netty_transport_native_io_uring42";
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
