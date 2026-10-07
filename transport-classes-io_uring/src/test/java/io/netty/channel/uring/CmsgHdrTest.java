/*
 * Copyright 2026 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link CmsgHdr#numFds(long, int, int)}, the pure fd-count computation used to detect a peer packing
 * more than one file descriptor into the single {@code SCM_RIGHTS} control message that Netty's io_uring
 * transport sizes for exactly one fd (CVE-2026-45536 equivalent for io_uring; the epoll/kqueue transports
 * already guard against this in {@code netty_unix_socket_recvFd()}).
 * <p>
 * This test intentionally never touches the {@link Native} class (which loads the native library) so it can run
 * without the io_uring native library being available, exactly like {@link LoadClassTest}.
 */
// CmsgHdr is compiled with release 9 (see the module's pom.xml), so it cannot be loaded on a Java 8
// runtime; gate the whole class since every test here touches CmsgHdr.
@EnabledForJreRange(min = JRE.JAVA_9)
public class CmsgHdrTest {

    // sizeof(int) is 4 on every Linux architecture io_uring supports.
    private static final int SIZEOF_INT = 4;
    // CMSG_LEN(sizeof(int)) on 64-bit Linux: CMSG_ALIGN(sizeof(struct cmsghdr)) [16] + sizeof(int) [4].
    private static final int CMSG_LEN_FOR_FD_64BIT = 20;
    // CMSG_LEN(sizeof(int)) on 32-bit Linux: CMSG_ALIGN(sizeof(struct cmsghdr)) [12] + sizeof(int) [4].
    private static final int CMSG_LEN_FOR_FD_32BIT = 16;

    @Test
    public void singleFdIsAccepted() {
        assertEquals(1, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
        assertEquals(1, CmsgHdr.numFds(CMSG_LEN_FOR_FD_32BIT, CMSG_LEN_FOR_FD_32BIT, SIZEOF_INT));
    }

    @Test
    public void twoFdsPackedIntoBufferSizedForOneAreDetected() {
        // This is the crux of the vulnerability: on 64-bit Linux CMSG_SPACE(sizeof(int)) (the buffer Netty
        // allocates) and CMSG_SPACE(2 * sizeof(int)) are both 24 bytes because of 8-byte alignment, so a peer
        // can pack 2 fds into the same space without the kernel ever setting MSG_CTRUNC. cmsg_len for that
        // 2-fd message is CMSG_LEN_FOR_FD + sizeof(int) == 24.
        assertEquals(2, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT + 4, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
    }

    @Test
    public void manyFdsPackedIntoOneCmsgAreDetected() {
        assertEquals(5, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT + 4 * 4, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
    }

    @Test
    public void cmsgLenSmallerThanOneFdIsTruncated() {
        assertEquals(-1, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT - 1, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
        assertEquals(-1, CmsgHdr.numFds(0, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
    }

    @Test
    public void cmsgLenNotAWholeNumberOfFdsIsMalformed() {
        assertEquals(-1, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT + 1, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
        assertEquals(-1, CmsgHdr.numFds(CMSG_LEN_FOR_FD_64BIT + 3, CMSG_LEN_FOR_FD_64BIT, SIZEOF_INT));
    }

    @Test
    public void truncatedMessageWithDeliveredFdsClosesThem() {
        // MSG_CTRUNC does not mean no fd was delivered, so the delivered one needs to be closed.
        assertEquals(1, CmsgHdr.numFdsToClose(1, true));
        assertEquals(2, CmsgHdr.numFdsToClose(2, true));
    }

    @Test
    public void truncatedMessageWithoutDeliveredFdsClosesNothing() {
        assertEquals(0, CmsgHdr.numFdsToClose(-1, true));
        assertEquals(0, CmsgHdr.numFdsToClose(0, true));
    }

    @Test
    public void multipleFdsAreAllClosedEvenIfNotTruncated() {
        assertEquals(2, CmsgHdr.numFdsToClose(2, false));
        assertEquals(5, CmsgHdr.numFdsToClose(5, false));
    }

    @Test
    public void singleFdInNonTruncatedMessageIsNotClosed() {
        assertEquals(0, CmsgHdr.numFdsToClose(1, false));
        assertEquals(0, CmsgHdr.numFdsToClose(-1, false));
    }
}
