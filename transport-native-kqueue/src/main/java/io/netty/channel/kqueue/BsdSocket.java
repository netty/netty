/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Socket;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.netty.channel.kqueue.AcceptFilter.PLATFORM_UNSUPPORTED;
import static io.netty.channel.kqueue.Native.CONNECT_TCP_FASTOPEN;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.NativeInetAddress.ipv4MappedIpv6Address;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A socket which provides access BSD native methods.
 */
final class BsdSocket extends Socket {

    // These limits are just based on observations. I couldn't find anything in header files which formally
    // define these limits.
    private static final int APPLE_SND_LOW_AT_MAX = 1 << 17;
    private static final int FREEBSD_SND_LOW_AT_MAX = 1 << 15;
    static final int BSD_SND_LOW_AT_MAX = Math.min(APPLE_SND_LOW_AT_MAX, FREEBSD_SND_LOW_AT_MAX);
    /**
     * The `endpoints` structure passed to `connectx(2)` has an optional "source interface" field,
     * which is the index of the network interface to use.
     * According to `if_nametoindex(3)`, the value 0 is used when no interface is specified.
     */
    private static final int UNSPECIFIED_SOURCE_INTERFACE = 0;

    BsdSocket(int fd) {
        super(fd);
    }

    void setAcceptFilter(AcceptFilter acceptFilter) throws IOException {
        setAcceptFilter(intValue(), acceptFilter.filterName(), acceptFilter.filterArgs());
    }

    void setTcpNoPush(boolean tcpNoPush) throws IOException {
        setTcpNoPush(intValue(), tcpNoPush ? 1 : 0);
    }

    void setSndLowAt(int lowAt) throws IOException {
        setSndLowAt(intValue(), lowAt);
    }

    boolean isTcpNoPush() throws IOException {
        return getTcpNoPush(intValue()) != 0;
    }

    int getSndLowAt() throws IOException {
        return getSndLowAt(intValue());
    }

    AcceptFilter getAcceptFilter() throws IOException {
        String[] result = getAcceptFilter(intValue());
        return result == null ? PLATFORM_UNSUPPORTED : new AcceptFilter(result[0], result[1]);
    }

    PeerCredentials getPeerCredentials() throws IOException {
        return getPeerCredentials(intValue());
    }

    long sendFile(DefaultFileRegion src, long baseOffset, long offset, long length) throws IOException {
        // Open the file-region as it may be created via the lazy constructor. This is needed as we directly access
        // the FileChannel field via JNI.
        src.open();

        long res = sendFile(intValue(), src, baseOffset, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("sendfile", (int) res);
    }

    int connectx(InetSocketAddress source, InetSocketAddress destination, IovArray data, boolean tcpFastOpen)
            throws IOException {
        int flags = tcpFastOpen ? CONNECT_TCP_FASTOPEN : 0;
        return connectx(source, destination, data, flags);
    }

    private int connectx(InetSocketAddress source, InetSocketAddress destination, IovArray data, int flags)
            throws IOException {
        checkNotNull(destination, "Destination InetSocketAddress cannot be null.");

        boolean sourceIPv6;
        byte[] sourceAddress;
        int sourceScopeId;
        int sourcePort;
        if (source == null) {
            sourceIPv6 = false;
            sourceAddress = null;
            sourceScopeId = 0;
            sourcePort = 0;
        } else {
            InetAddress sourceInetAddress = source.getAddress();
            sourceIPv6 = sourceInetAddress instanceof Inet6Address;
            if (sourceIPv6) {
                sourceAddress = sourceInetAddress.getAddress();
                sourceScopeId = ((Inet6Address) sourceInetAddress).getScopeId();
            } else {
                // convert to ipv4 mapped ipv6 address;
                sourceScopeId = 0;
                sourceAddress = ipv4MappedIpv6Address(sourceInetAddress.getAddress());
            }
            sourcePort = source.getPort();
        }

        InetAddress destinationInetAddress = destination.getAddress();
        boolean destinationIPv6 = destinationInetAddress instanceof Inet6Address;
        byte[] destinationAddress;
        int destinationScopeId;
        if (destinationIPv6) {
            destinationAddress = destinationInetAddress.getAddress();
            destinationScopeId = ((Inet6Address) destinationInetAddress).getScopeId();
        } else {
            // convert to ipv4 mapped ipv6 address;
            destinationScopeId = 0;
            destinationAddress = ipv4MappedIpv6Address(destinationInetAddress.getAddress());
        }
        int destinationPort = source.getPort();

        long iovAddress;
        int iovCount;
        int iovDataLength;
        if (data == null || data.count() == 0) {
            iovAddress = 0;
            iovCount = 0;
            iovDataLength = 0;
        } else {
            iovAddress = data.memoryAddress(0);
            iovCount = data.count();
            long size = data.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("IovArray.size() too big: " + size + " bytes.");
            }
            iovDataLength = (int) size;
        }

        int result = connectx(intValue(),
                UNSPECIFIED_SOURCE_INTERFACE, sourceIPv6, sourceAddress, sourceScopeId, sourcePort,
                destinationIPv6, destinationAddress, destinationScopeId, destinationPort,
                flags, iovAddress, iovCount, iovDataLength);
        if (result < 0) {
            return ioResult("connectx", -result);
        }
        return result;
    }

    public static BsdSocket newSocketStream() {
        return new BsdSocket(newSocketStream0());
    }

    public static BsdSocket newSocketDgram() {
        return new BsdSocket(newSocketDgram0());
    }

    public static BsdSocket newSocketDomain() {
        return new BsdSocket(newSocketDomain0());
    }

    public static BsdSocket newSocketDomainDgram() {
        return new BsdSocket(newSocketDomainDgram0());
    }

    private static native long sendFile(int socketFd, DefaultFileRegion src, long baseOffset,
                                        long offset, long length) throws IOException;

    /**
     * @return If successful, zero or positive number of bytes transfered, otherwise negative errno.
     */
    private static native int connectx(
            int socketFd,
            // sa_endpoints_t *endpoints:
            int sourceInterface,
            boolean sourceIPv6, byte[] sourceAddress, int sourceScopeId, int sourcePort,
            boolean destinationIPv6, byte[] destinationAddress, int destinationScopeId, int destinationPort,
            // sae_associd_t associd is reserved
            int flags,
            long iovAddress, int iovCount, int iovDataLength
            // sae_connid_t *connid is reserved
    );

    private static native String[] getAcceptFilter(int fd) throws IOException;
    private static native int getTcpNoPush(int fd) throws IOException;
    private static native int getSndLowAt(int fd) throws IOException;
    private static native PeerCredentials getPeerCredentials(int fd) throws IOException;

    private static native void setAcceptFilter(int fd, String filterName, String filterArgs) throws IOException;
    private static native void setTcpNoPush(int fd, int tcpNoPush) throws IOException;
    private static native void setSndLowAt(int fd, int lowAt) throws IOException;
}
