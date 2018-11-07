/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.Socket;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;

import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollerr;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollet;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollin;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollout;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollrdhup;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingSendmmsg;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingTcpFastopen;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.kernelVersion;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.tcpMd5SigMaxKeyLen;
import static io.netty.channel.unix.Errors.ERRNO_EPIPE_NEGATIVE;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newConnectionResetException;
import static io.netty.channel.unix.Errors.newIOException;

/**
 * Native helper methods
 * <p><strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link NativeStaticallyReferencedJniMethods}.
 */
public final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);

    static {
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            offsetofEpollData();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }
        Socket.initialize();
    }

    // EventLoop operations and constants
    public static final int EPOLLIN = epollin();
    public static final int EPOLLOUT = epollout();
    public static final int EPOLLRDHUP = epollrdhup();
    public static final int EPOLLET = epollet();
    public static final int EPOLLERR = epollerr();

    public static final boolean IS_SUPPORTING_SENDMMSG = isSupportingSendmmsg();
    public static final boolean IS_SUPPORTING_TCP_FASTOPEN = isSupportingTcpFastopen();
    public static final int TCP_MD5SIG_MAXKEYLEN = tcpMd5SigMaxKeyLen();
    public static final String KERNEL_VERSION = kernelVersion();

    private static final NativeIoException SENDMMSG_CONNECTION_RESET_EXCEPTION;
    private static final NativeIoException SPLICE_CONNECTION_RESET_EXCEPTION;
    private static final ClosedChannelException SENDMMSG_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "sendmmsg(...)");
    private static final ClosedChannelException SPLICE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "splice(...)");

    static {
        SENDMMSG_CONNECTION_RESET_EXCEPTION = newConnectionResetException("syscall:sendmmsg(...)",
                ERRNO_EPIPE_NEGATIVE);
        SPLICE_CONNECTION_RESET_EXCEPTION = newConnectionResetException("syscall:splice(...)",
                ERRNO_EPIPE_NEGATIVE);
    }

    public static FileDescriptor newEventFd() {
        return new FileDescriptor(eventFd());
    }

    public static FileDescriptor newTimerFd() {
        return new FileDescriptor(timerFd());
    }

    private static native int eventFd();
    private static native int timerFd();
    public static native void eventFdWrite(int fd, long value);
    public static native void eventFdRead(int fd);
    static native void timerFdRead(int fd);

    public static FileDescriptor newEpollCreate() {
        return new FileDescriptor(epollCreate());
    }

    private static native int epollCreate();

    public static int epollWait(FileDescriptor epollFd, EpollEventArray events, FileDescriptor timerFd,
                                int timeoutSec, int timeoutNs) throws IOException {
        int ready = epollWait0(epollFd.intValue(), events.memoryAddress(), events.length(), timerFd.intValue(),
                               timeoutSec, timeoutNs);
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }
    private static native int epollWait0(int efd, long address, int len, int timerFd, int timeoutSec, int timeoutNs);

    /**
     * Non-blocking variant of
     * {@link #epollWait(FileDescriptor, EpollEventArray, FileDescriptor, int, int)}
     * that will also hint to processor we are in a busy-wait loop.
     */
    public static int epollBusyWait(FileDescriptor epollFd, EpollEventArray events) throws IOException {
        int ready = epollBusyWait0(epollFd.intValue(), events.memoryAddress(), events.length());
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }

    private static native int epollBusyWait0(int efd, long address, int len);

    public static void epollCtlAdd(int efd, final int fd, final int flags) throws IOException {
        int res = epollCtlAdd0(efd, fd, flags);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlAdd0(int efd, final int fd, final int flags);

    public static void epollCtlMod(int efd, final int fd, final int flags) throws IOException {
        int res = epollCtlMod0(efd, fd, flags);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlMod0(int efd, final int fd, final int flags);

    public static void epollCtlDel(int efd, final int fd) throws IOException {
        int res = epollCtlDel0(efd, fd);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlDel0(int efd, final int fd);

    // File-descriptor operations
    public static int splice(int fd, long offIn, int fdOut, long offOut, long len) throws IOException {
        int res = splice0(fd, offIn, fdOut, offOut, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("splice", res, SPLICE_CONNECTION_RESET_EXCEPTION, SPLICE_CLOSED_CHANNEL_EXCEPTION);
    }

    private static native int splice0(int fd, long offIn, int fdOut, long offOut, long len);

    public static int sendmmsg(
            int fd, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len) throws IOException {
        int res = sendmmsg0(fd, msgs, offset, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendmmsg", res, SENDMMSG_CONNECTION_RESET_EXCEPTION, SENDMMSG_CLOSED_CHANNEL_EXCEPTION);
    }

    private static native int sendmmsg0(
            int fd, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len);

    // epoll_event related
    public static native int sizeofEpollEvent();
    public static native int offsetofEpollData();

    private static void loadNativeLibrary() {
        String name = SystemPropertyUtil.get("os.name").toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        String staticLibName = "netty_transport_native_epoll";
        String sharedLibName = staticLibName + '_' + PlatformDependent.normalizedArch();
        ClassLoader cl = PlatformDependent.getClassLoader(Native.class);
        try {
            NativeLibraryLoader.load(sharedLibName, cl);
        } catch (UnsatisfiedLinkError e1) {
            try {
                NativeLibraryLoader.load(staticLibName, cl);
                logger.debug("Failed to load {}", sharedLibName, e1);
            } catch (UnsatisfiedLinkError e2) {
                ThrowableUtil.addSuppressed(e1, e2);
                throw e1;
            }
        }
    }

    private Native() {
        // utility
    }
}
