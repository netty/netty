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

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.Errors.NativeIoException;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.NativeInetAddress;
import io.netty.util.internal.ThrowableUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;

import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollerr;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollet;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollin;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollout;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.epollrdhup;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.iovMax;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingSendmmsg;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.isSupportingTcpFastopen;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.kernelVersion;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.ssizeMax;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.tcpMd5SigMaxKeyLen;
import static io.netty.channel.epoll.NativeStaticallyReferencedJniMethods.uioMaxIov;
import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EPIPE_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EWOULDBLOCK_NEGATIVE;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newConnectionResetException;
import static io.netty.channel.unix.Errors.newIOException;

/**
 * Native helper methods
 * <p><strong>Internal usage only!</strong>
 * <p>Static members which call JNI methods must be defined in {@link NativeStaticallyReferencedJniMethods}.
 */
public final class Native {
    static {
        try {
            // First, try calling a side-effect free JNI method to see if the library was already
            // loaded by the application.
            offsetofEpollData();
        } catch (UnsatisfiedLinkError ignore) {
            // The library was not previously loaded, load it now.
            loadNativeLibrary();
        }
    }

    // EventLoop operations and constants
    public static final int EPOLLIN = epollin();
    public static final int EPOLLOUT = epollout();
    public static final int EPOLLRDHUP = epollrdhup();
    public static final int EPOLLET = epollet();
    public static final int EPOLLERR = epollerr();

    public static final int IOV_MAX = iovMax();
    public static final int UIO_MAX_IOV = uioMaxIov();
    public static final boolean IS_SUPPORTING_SENDMMSG = isSupportingSendmmsg();
    public static final boolean IS_SUPPORTING_TCP_FASTOPEN = isSupportingTcpFastopen();
    public static final long SSIZE_MAX = ssizeMax();
    public static final int TCP_MD5SIG_MAXKEYLEN = tcpMd5SigMaxKeyLen();
    public static final String KERNEL_VERSION = kernelVersion();

    private static final NativeIoException SENDFILE_CONNECTION_RESET_EXCEPTION;
    private static final NativeIoException SENDMMSG_CONNECTION_RESET_EXCEPTION;
    private static final NativeIoException SPLICE_CONNECTION_RESET_EXCEPTION;
    private static final ClosedChannelException SENDFILE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "sendfile(...)");
    private static final ClosedChannelException SENDMMSG_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "sendmmsg(...)");
    private static final ClosedChannelException SPLICE_CLOSED_CHANNEL_EXCEPTION = ThrowableUtil.unknownStackTrace(
            new ClosedChannelException(), Native.class, "splice(...)");

    static {
        SENDFILE_CONNECTION_RESET_EXCEPTION = newConnectionResetException("syscall:sendfile(...)",
                ERRNO_EPIPE_NEGATIVE);
        SENDMMSG_CONNECTION_RESET_EXCEPTION = newConnectionResetException("syscall:sendmmsg(...)",
                ERRNO_EPIPE_NEGATIVE);
        SPLICE_CONNECTION_RESET_EXCEPTION = newConnectionResetException("syscall:splice(...)",
                ERRNO_EPIPE_NEGATIVE);
    }

    public static FileDescriptor newEventFd() {
        return new FileDescriptor(eventFd());
    }

    private static native int eventFd();
    public static native void eventFdWrite(int fd, long value);
    public static native void eventFdRead(int fd);

    public static FileDescriptor newEpollCreate() {
        return new FileDescriptor(epollCreate());
    }

    private static native int epollCreate();

    public static int epollWait(int efd, EpollEventArray events, int timeout) throws IOException {
        int ready = epollWait0(efd, events.memoryAddress(), events.length(), timeout);
        if (ready < 0) {
            throw newIOException("epoll_wait", ready);
        }
        return ready;
    }
    private static native int epollWait0(int efd, long address, int len, int timeout);

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

    public static long sendfile(
            int dest, DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException {
        // Open the file-region as it may be created via the lazy constructor. This is needed as we directly access
        // the FileChannel field directly via JNI
        src.open();

        long res = sendfile0(dest, src, baseOffset, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendfile", (int) res, SENDFILE_CONNECTION_RESET_EXCEPTION, SENDFILE_CLOSED_CHANNEL_EXCEPTION);
    }

    private static native long sendfile0(
            int dest, DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException;

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

    public static int recvFd(int fd) throws IOException {
        int res = recvFd0(fd);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }

        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return 0;
        }
        throw newIOException("recvFd", res);
    }

    private static native int recvFd0(int fd);

    public static int sendFd(int socketFd, int fd) throws IOException {
        int res = sendFd0(socketFd, fd);
        if (res >= 0) {
            return res;
        }
        if (res == ERRNO_EAGAIN_NEGATIVE || res == ERRNO_EWOULDBLOCK_NEGATIVE) {
            // Everything consumed so just return -1 here.
            return -1;
        }
        throw newIOException("sendFd", res);
    }

    private static native int sendFd0(int socketFd, int fd);

    // Socket option operations
    public static native int isReuseAddress(int fd) throws IOException;
    public static native int isReusePort(int fd) throws IOException;
    public static native int getTcpNotSentLowAt(int fd) throws IOException;
    public static native int getTrafficClass(int fd) throws IOException;
    public static native int isBroadcast(int fd) throws IOException;
    public static native int getTcpKeepIdle(int fd) throws IOException;
    public static native int getTcpKeepIntvl(int fd) throws IOException;
    public static native int getTcpKeepCnt(int fd) throws IOException;
    public static native int getTcpUserTimeout(int milliseconds) throws IOException;
    public static native int isIpFreeBind(int fd)throws IOException;

    public static native void setReuseAddress(int fd, int reuseAddress) throws IOException;
    public static native void setReusePort(int fd, int reuseAddress) throws IOException;
    public static native void setTcpFastopen(int fd, int tcpFastopenBacklog) throws IOException;
    public static native void setTcpNotSentLowAt(int fd, int tcpNotSentLowAt) throws IOException;
    public static native void setTrafficClass(int fd, int tcpNoDelay) throws IOException;
    public static native void setBroadcast(int fd, int broadcast) throws IOException;
    public static native void setTcpKeepIdle(int fd, int seconds) throws IOException;
    public static native void setTcpKeepIntvl(int fd, int seconds) throws IOException;
    public static native void setTcpKeepCnt(int fd, int probes) throws IOException;
    public static native void setTcpUserTimeout(int fd, int milliseconds)throws IOException;
    public static native void setIpFreeBind(int fd, int freeBind) throws IOException;
    public static void tcpInfo(int fd, EpollTcpInfo info) throws IOException {
        tcpInfo0(fd, info.info);
    }

    private static native void tcpInfo0(int fd, int[] array) throws IOException;

    public static void setTcpMd5Sig(int fd, InetAddress address, byte[] key) throws IOException {
        final NativeInetAddress a = NativeInetAddress.newInstance(address);
        setTcpMd5Sig0(fd, a.address(), a.scopeId(), key);
    }

    private static native void setTcpMd5Sig0(int fd, byte[] address, int scopeId, byte[] key) throws IOException;

    // epoll_event related
    public static native int sizeofEpollEvent();
    public static native int offsetofEpollData();

    private Native() {
        // utility
    }

    private static void loadNativeLibrary() {
        String name = SystemPropertyUtil.get("os.name").toLowerCase(Locale.UK).trim();
        if (!name.startsWith("linux")) {
            throw new IllegalStateException("Only supported on Linux");
        }
        NativeLibraryLoader.load(SystemPropertyUtil.get("io.netty.packagePrefix", "").replace('.', '-') +
            "netty-transport-native-epoll", PlatformDependent.getClassLoader(Native.class));
    }
}
