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

import io.netty.channel.unix.Errors;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <pre>{@code
 * struct cmsghdr {
 *     socklen_t cmsg_len;    // data byte count, including header
 *     int cmsg_level;  //originating protocol
 *     int cmsg_type;   // protocol-specific type
 *     // followed by unsigned char cmsg_data[];
 * };
 * }</pre>
 */
final class CmsgHdr {

    private CmsgHdr() { }

    static void write(ByteBuffer cmsghdr, int cmsgHdrDataOffset,
                      int cmsgLen, int cmsgLevel, int cmsgType, short segmentSize) {
        int cmsghdrPosition = cmsghdr.position();
        if (Native.SIZEOF_SIZE_T == 4) {
            cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN, cmsgLen);
        } else {
            assert Native.SIZEOF_SIZE_T == 8;
            cmsghdr.putLong(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN, cmsgLen);
        }
        cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEVEL, cmsgLevel);
        cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_TYPE, cmsgType);
        cmsghdr.putShort(cmsghdrPosition + cmsgHdrDataOffset, segmentSize);
    }

    static void writeScmRights(ByteBuffer cmsghdr, int cmsgHdrDataOffset, int fd) {
        int cmsghdrPosition = cmsghdr.position();
        if (Native.SIZEOF_SIZE_T == 4) {
            cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN, Native.CMSG_LEN_FOR_FD);
        } else {
            assert Native.SIZEOF_SIZE_T == 8;
            cmsghdr.putLong(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN, Native.CMSG_LEN_FOR_FD);
        }
        cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEVEL, Native.SOL_SOCKET);
        cmsghdr.putInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_TYPE, Native.SCM_RIGHTS);
        cmsghdr.putInt(cmsghdrPosition + cmsgHdrDataOffset, fd);
    }

    /**
     * Reads the file descriptor delivered via a {@code SCM_RIGHTS} control message.
     * <p>
     * A malicious (or buggy) peer can pack more than one file descriptor into the single control message that
     * Netty only ever sizes for one fd. On 64-bit Linux {@code CMSG_SPACE(sizeof(int))} and
     * {@code CMSG_SPACE(2 * sizeof(int))} are both 24 bytes (due to 8 byte alignment), so the kernel does not
     * report {@code MSG_CTRUNC} even though two fds were delivered. If that is not detected the surplus fd is
     * never closed and leaks for the lifetime of the process (see CVE-2026-45536 for the equivalent issue that
     * was fixed for the epoll/kqueue transports in {@code netty_unix_socket_recvFd()}).
     * <p>
     * To guard against this we validate the cmsg level/type, compute the real number of fds from
     * {@code cmsg_len} and, if more than one fd (or a truncated message) was received, close every fd we got
     * and fail instead of silently returning a partial (and leaking) result.
     *
     * @throws IOException if the control message did not carry exactly one well-formed {@code SCM_RIGHTS} fd.
     */
    static int readScmRights(ByteBuffer cmsghdr, int cmsgHdrDataOffset, boolean truncated) throws IOException {
        int cmsghdrPosition = cmsghdr.position();
        long cmsgLen = Native.SIZEOF_SIZE_T == 4
                ? cmsghdr.getInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN) & 0xFFFFFFFFL
                : cmsghdr.getLong(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEN);
        int cmsgLevel = cmsghdr.getInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_LEVEL);
        int cmsgType = cmsghdr.getInt(cmsghdrPosition + Native.CMSG_OFFSETOF_CMSG_TYPE);

        if (cmsgLevel != Native.SOL_SOCKET || cmsgType != Native.SCM_RIGHTS) {
            throw new IOException(
                    "Received unexpected cmsg (level: " + cmsgLevel + ", type: " + cmsgType + ')');
        }

        // MSG_CTRUNC only means that some of the control data was discarded, the kernel still reports the fds it did
        // deliver via cmsg_len. So always compute nfds from cmsg_len to be able to close whatever was delivered.
        int nfds = numFds(cmsgLen, Native.CMSG_LEN_FOR_FD, Native.SIZEOF_INT);
        if (truncated || nfds != 1) {
            IOException error = new IOException(truncated
                    ? "Received a truncated SCM_RIGHTS cmsg (cmsg_len: " + cmsgLen + ')'
                    : nfds < 0
                        ? "Received a malformed SCM_RIGHTS cmsg (cmsg_len: " + cmsgLen + ')'
                        : "Received " + nfds + " file descriptors in a single SCM_RIGHTS cmsg, expected 1");
            // Close every fd that was actually delivered so nothing leaks, even though we reject the message.
            int toClose = numFdsToClose(nfds, truncated);
            for (int i = 0; i < toClose; i++) {
                closeQuietly(cmsghdr.getInt(cmsghdrPosition + cmsgHdrDataOffset + i * Native.SIZEOF_INT), error);
            }
            throw error;
        }
        return cmsghdr.getInt(cmsghdrPosition + cmsgHdrDataOffset);
    }

    /**
     * Computes how many file descriptors a {@code SCM_RIGHTS} cmsg of the given {@code cmsgLen} carries, given
     * {@code cmsgLenForFd}, the platform's {@code CMSG_LEN(sizeof(int))} (the length of a cmsg holding exactly
     * one fd), and {@code sizeofInt}, the platform's {@code sizeof(int)}. Returns {@code -1} if {@code cmsgLen}
     * is smaller than one fd or not a whole number of fds (i.e. truncated or malformed).
     * <p>
     * Pure function of primitives (no native/JNI dependency) so it can be unit tested in isolation.
     */
    static int numFds(long cmsgLen, int cmsgLenForFd, int sizeofInt) {
        long dataLen = cmsgLen - (cmsgLenForFd - sizeofInt);
        if (dataLen < sizeofInt || dataLen % sizeofInt != 0) {
            return -1;
        }
        return (int) (dataLen / sizeofInt);
    }

    /**
     * Returns the number of delivered fds that must be closed because the message is rejected: all of them if the
     * message was truncated (and at least one fd was delivered) or if more than one fd was delivered.
     * <p>
     * Pure function of primitives so it can be unit tested in isolation.
     */
    static int numFdsToClose(int nfds, boolean truncated) {
        return nfds > 0 && (truncated || nfds > 1) ? nfds : 0;
    }

    private static void closeQuietly(int fd, Throwable error) {
        if (fd < 0) {
            return;
        }
        int res = Native.close(fd);
        if (res < 0) {
            error.addSuppressed(Errors.newIOException("close", res));
        }
    }
}
