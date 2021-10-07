/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Socket;
import io.netty.channel.unix.Unix;
import io.netty.util.internal.ClassInitializerUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;

import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollerr;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollet;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollin;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollout;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollrdhup;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingRecvmmsg;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingSendmmsg;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.kernelVersion;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.tcpFastopenMode;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.tcpMd5SigMaxKeyLen;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newIOException;

/**
 * Native helper methods
 * <p><strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link NativeStaticallyReferencedJniMethods}.
 */
public final class Native {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Native.class);

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
        ClassInitializerUtil.tryLoadClasses(Native.class,
                // netty_epoll_linuxsocket
                PeerCredentials.class, DefaultFileRegion.class, FileChannel.class, java.io.FileDescriptor.class,
                // netty_epoll_native
                NativeDatagramPacketArray.NativeDatagramPacket.class
        );

        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            offsetofEpollData();
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
        Unix.registerInternal(new Runnable() {
            @Override
            public void run() {
                registerUnix();
            }
        });
    }

    private static native int registerUnix();

    // EventLoop operations and constants
    public static final int EPOLLIN = epollin();
    public static final int EPOLLOUT = epollout();
    public static final int EPOLLRDHUP = epollrdhup();
    public static final int EPOLLET = epollet();
    public static final int EPOLLERR = epollerr();

    public static final boolean IS_SUPPORTING_SENDMMSG = isSupportingSendmmsg();
    static final boolean IS_SUPPORTING_RECVMMSG = isSupportingRecvmmsg();
    static final boolean IS_SUPPORTING_UDP_SEGMENT = isSupportingUdpSegment();
    private static final int TFO_ENABLED_CLIENT_MASK = 0x1;
    private static final int TFO_ENABLED_SERVER_MASK = 0x2;
    private static final int TCP_FASTOPEN_MODE = tcpFastopenMode();
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
    /**
     * @deprecated Use {@link #IS_SUPPORTING_TCP_FASTOPEN_CLIENT} or {@link #IS_SUPPORTING_TCP_FASTOPEN_SERVER}.
     */
    @Deprecated
    public static final boolean IS_SUPPORTING_TCP_FASTOPEN = IS_SUPPORTING_TCP_FASTOPEN_CLIENT ||
            IS_SUPPORTING_TCP_FASTOPEN_SERVER;
    public static final int TCP_MD5SIG_MAXKEYLEN = tcpMd5SigMaxKeyLen();
    public static final String KERNEL_VERSION = kernelVersion();

    public static FileDescriptor newEventFd() {
        return new FileDescriptor(eventFd());
    }

    public static FileDescriptor newTimerFd() {
        return new FileDescriptor(timerFd());
    }

    private static native boolean isSupportingUdpSegment();
    private static native int eventFd();
    private static native int timerFd();
    public static native void eventFdWrite(int fd, long value);
    public static native void eventFdRead(int fd);
    static native void timerFdRead(int fd);
    static native void timerFdSetTime(int fd, int sec, int nsec) throws IOException;

    public static FileDescriptor newEpollCreate() {
        return new FileDescriptor(epollCreate());
    }

    private static native int epollCreate();

    /**
     * @deprecated this method is no longer supported. This functionality is internal to this package.
     */
    @Deprecated
    public static int epollWait(FileDescriptor epollFd, EpollEventArray events, FileDescriptor timerFd,
                                int timeoutSec, int timeoutNs) throws IOException {
        if (timeoutSec == 0 && timeoutNs == 0) {
            // Zero timeout => poll (aka return immediately)
            return epollWait(epollFd, events, 0);
        }
        if (timeoutSec == Integer.MAX_VALUE) {
            // Max timeout => wait indefinitely: disarm timerfd first
            timeoutSec = 0;
            timeoutNs = 0;
        }
        int ready = epollWait0(epollFd.intValue(), events.memoryAddress(), events.length(), timerFd.intValue(),
                               timeoutSec, timeoutNs);
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }

    static int epollWait(FileDescriptor epollFd, EpollEventArray events, boolean immediatePoll) throws IOException {
        return epollWait(epollFd, events, immediatePoll ? 0 : -1);
    }

    /**
     * This uses epoll's own timeout and does not reset/re-arm any timerfd
     */
    static int epollWait(FileDescriptor epollFd, EpollEventArray events, int timeoutMillis) throws IOException {
        int ready = epollWait(epollFd.intValue(), events.memoryAddress(), events.length(), timeoutMillis);
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }

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

    private static native int epollWait0(int efd, long address, int len, int timerFd, int timeoutSec, int timeoutNs);
    private static native int epollWait(int efd, long address, int len, int timeout);
    private static native int epollBusyWait0(int efd, long address, int len);

    public static void epollCtlAdd(int efd, final int fd, final int flags) throws IOException {
        int res = epollCtlAdd0(efd, fd, flags);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlAdd0(int efd, int fd, int flags);

    public static void epollCtlMod(int efd, final int fd, final int flags) throws IOException {
        int res = epollCtlMod0(efd, fd, flags);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlMod0(int efd, int fd, int flags);

    public static void epollCtlDel(int efd, final int fd) throws IOException {
        int res = epollCtlDel0(efd, fd);
        if (res < 0) {
            throw newIOException("epoll_ctl", res);
        }
    }
    private static native int epollCtlDel0(int efd, int fd);

    // File-descriptor operations
    public static int splice(int fd, long offIn, int fdOut, long offOut, long len) throws IOException {
        int res = splice0(fd, offIn, fdOut, offOut, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("splice", res);
    }

    private static native int splice0(int fd, long offIn, int fdOut, long offOut, long len);

    @Deprecated
    public static int sendmmsg(int fd, NativeDatagramPacketArray.NativeDatagramPacket[] msgs,
                               int offset, int len) throws IOException {
        return sendmmsg(fd, Socket.isIPv6Preferred(), msgs, offset, len);
    }

    static int sendmmsg(int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket[] msgs,
                               int offset, int len) throws IOException {
        int res = sendmmsg0(fd, ipv6, msgs, offset, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendmmsg", res);
    }

    private static native int sendmmsg0(
            int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len);

    static int recvmmsg(int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket[] msgs,
                        int offset, int len) throws IOException {
        int res = recvmmsg0(fd, ipv6, msgs, offset, len);
        if (res >= 0) {
            return res;
        }
        return ioResult("recvmmsg", res);
    }

    private static native int recvmmsg0(
            int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket[] msgs, int offset, int len);

    static int recvmsg(int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket packet) throws IOException {
        int res = recvmsg0(fd, ipv6, packet);
        if (res >= 0) {
            return res;
        }
        return ioResult("recvmsg", res);
    }

    private static native int recvmsg0(
            int fd, boolean ipv6, NativeDatagramPacketArray.NativeDatagramPacket msg);

    // epoll_event related
    public static native int sizeofEpollEvent();
    public static native int offsetofEpollData();

    private static void loadNativeLibrary() {
        String name = PlatformDependent.normalizedOs();
        if (!"linux".equals(name)) {
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
