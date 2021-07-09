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
import io.netty.channel.unix.PeerCredentials;
import io.netty.channel.unix.Socket;

import java.io.IOException;

import static io.netty.channel.kqueue.AcceptFilter.PLATFORM_UNSUPPORTED;
import static io.netty.channel.unix.Errors.ioResult;

/**
 * A socket which provides access BSD native methods.
 */
final class BsdSocket extends Socket {

    // These limits are just based on observations. I couldn't find anything in header files which formally
    // define these limits.
    private static final int APPLE_SND_LOW_AT_MAX = 1 << 17;
    private static final int FREEBSD_SND_LOW_AT_MAX = 1 << 15;
    static final int BSD_SND_LOW_AT_MAX = Math.min(APPLE_SND_LOW_AT_MAX, FREEBSD_SND_LOW_AT_MAX);

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

    boolean isTcpNoPush() throws IOException  {
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

    private static native String[] getAcceptFilter(int fd) throws IOException;
    private static native int getTcpNoPush(int fd) throws IOException;
    private static native int getSndLowAt(int fd) throws IOException;
    private static native PeerCredentials getPeerCredentials(int fd) throws IOException;

    private static native void setAcceptFilter(int fd, String filterName, String filterArgs) throws IOException;
    private static native void setTcpNoPush(int fd, int tcpNoPush) throws IOException;
    private static native void setSndLowAt(int fd, int lowAt) throws IOException;
}
