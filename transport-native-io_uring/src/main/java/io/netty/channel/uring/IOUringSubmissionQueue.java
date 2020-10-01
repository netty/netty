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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.channel.uring.UserData.encode;
import static java.lang.Math.max;
import static java.lang.Math.min;

final class IOUringSubmissionQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IOUringSubmissionQueue.class);

    private static final long SQE_SIZE = 64;
    private static final int INT_SIZE = Integer.BYTES; //no 32 Bit support?
    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    //these offsets are used to access specific properties
    //SQE https://github.com/axboe/liburing/blob/master/src/include/liburing/io_uring.h#L21
    private static final int SQE_OP_CODE_FIELD = 0;
    private static final int SQE_FLAGS_FIELD = 1;
    private static final int SQE_IOPRIO_FIELD = 2; // u16
    private static final int SQE_FD_FIELD = 4; // s32
    private static final int SQE_OFFSET_FIELD = 8;
    private static final int SQE_ADDRESS_FIELD = 16;
    private static final int SQE_LEN_FIELD = 24;
    private static final int SQE_RW_FLAGS_FIELD = 28;
    private static final int SQE_USER_DATA_FIELD = 32;
    private static final int SQE_PAD_FIELD = 40;

    private static final int KERNEL_TIMESPEC_TV_SEC_FIELD = 0;
    private static final int KERNEL_TIMESPEC_TV_NSEC_FIELD = 8;

    //these unsigned integer pointers(shared with the kernel) will be changed by the kernel
    private final long kHeadAddress;
    private final long kTailAddress;
    private final long kFlagsAddress;
    private final long kDroppedAddress;
    private final long kArrayAddress;
    final long submissionQueueArrayAddress;

    final int ringEntries;
    private final int ringMask; // = ringEntries - 1

    final int ringSize;
    final long ringAddress;
    final int ringFd;
    private final Runnable submissionCallback;
    private final long timeoutMemoryAddress;
    private final int iosqeAsyncThreshold;
    private int numHandledFds;
    private int head;
    private int tail;

    IOUringSubmissionQueue(long kHeadAddress, long kTailAddress, long kRingMaskAddress, long kRingEntriesAddress,
                           long kFlagsAddress, long kDroppedAddress, long kArrayAddress,
                           long submissionQueueArrayAddress, int ringSize, long ringAddress, int ringFd,
                           int iosqeAsyncThreshold, Runnable submissionCallback) {
        this.kHeadAddress = kHeadAddress;
        this.kTailAddress = kTailAddress;
        this.kFlagsAddress = kFlagsAddress;
        this.kDroppedAddress = kDroppedAddress;
        this.kArrayAddress = kArrayAddress;
        this.submissionQueueArrayAddress = submissionQueueArrayAddress;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;
        this.submissionCallback = submissionCallback;
        this.ringEntries = PlatformDependent.getIntVolatile(kRingEntriesAddress);
        this.ringMask = PlatformDependent.getIntVolatile(kRingMaskAddress);
        this.head = PlatformDependent.getIntVolatile(kHeadAddress);
        this.tail = PlatformDependent.getIntVolatile(kTailAddress);

        this.timeoutMemoryAddress = PlatformDependent.allocateMemory(KERNEL_TIMESPEC_SIZE);
        this.iosqeAsyncThreshold = iosqeAsyncThreshold;

        // Zero the whole SQE array first
        PlatformDependent.setMemory(submissionQueueArrayAddress, ringEntries * SQE_SIZE, (byte) 0);

        // Fill SQ array indices (1-1 with SQE array) and set nonzero constant SQE fields
        long address = kArrayAddress;
        for (int i = 0; i < ringEntries; i++, address += INT_SIZE) {
            PlatformDependent.putInt(address, i);
        }
    }

    void incrementHandledFds() {
        numHandledFds++;
    }

    void decrementHandledFds() {
        numHandledFds--;
        assert numHandledFds >= 0;
    }

    private int flags() {
        return numHandledFds < iosqeAsyncThreshold ? 0 : Native.IOSQE_ASYNC;
    }

    private boolean enqueueSqe(int op, int flags, int rwFlags, int fd,
                               long bufferAddress, int length, long offset, short data) {
        int pending = tail - head;
        boolean submit = pending == ringEntries;
        if (submit) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        long sqe = submissionQueueArrayAddress + (tail++ & ringMask) * SQE_SIZE;
        setData(sqe, op, flags, rwFlags, fd, bufferAddress, length, offset, data);
        return submit;
    }

    private void setData(long sqe, int op, int flags, int rwFlags, int fd, long bufferAddress, int length,
                         long offset, short data) {
        //set sqe(submission queue) properties

        PlatformDependent.putByte(sqe + SQE_OP_CODE_FIELD, (byte) op);
        PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, (byte) flags);
        // This constant is set up-front
        //PlatformDependent.putShort(sqe + SQE_IOPRIO_FIELD, (short) 0);
        PlatformDependent.putInt(sqe + SQE_FD_FIELD, fd);
        PlatformDependent.putLong(sqe + SQE_OFFSET_FIELD, offset);
        PlatformDependent.putLong(sqe + SQE_ADDRESS_FIELD, bufferAddress);
        PlatformDependent.putInt(sqe + SQE_LEN_FIELD, length);
        PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, rwFlags);
        long userData = encode(fd, op, data);
        PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, userData);

        logger.trace("UserDataField: {}", userData);
        logger.trace("BufferAddress: {}", bufferAddress);
        logger.trace("Length: {}", length);
        logger.trace("Offset: {}", offset);
    }

    boolean addTimeout(long nanoSeconds, short extraData) {
        setTimeout(nanoSeconds);
        return enqueueSqe(Native.IORING_OP_TIMEOUT, 0, 0, -1, timeoutMemoryAddress, 1, 0, extraData);
    }

    boolean addPollIn(int fd) {
        return addPoll(fd, Native.POLLIN);
    }

    boolean addPollRdHup(int fd) {
        return addPoll(fd, Native.POLLRDHUP);
    }

    boolean addPollOut(int fd) {
        return addPoll(fd, Native.POLLOUT);
    }

    private boolean addPoll(int fd, int pollMask) {
        return enqueueSqe(Native.IORING_OP_POLL_ADD, 0, pollMask, fd, 0, 0, 0, (short) pollMask);
    }

    boolean addRecvmsg(int fd, long msgHdr, short extraData) {
        return enqueueSqe(Native.IORING_OP_RECVMSG, flags(), 0, fd, msgHdr, 1, 0, extraData);
    }

    boolean addSendmsg(int fd, long msgHdr, short extraData) {
        return enqueueSqe(Native.IORING_OP_SENDMSG, flags(), 0, fd, msgHdr, 1, 0, extraData);
    }

    boolean addRead(int fd, long bufferAddress, int pos, int limit, short extraData) {
        return enqueueSqe(Native.IORING_OP_READ, flags(), 0, fd, bufferAddress + pos, limit - pos, 0, extraData);
    }

    boolean addWrite(int fd, long bufferAddress, int pos, int limit, short extraData) {
        return enqueueSqe(Native.IORING_OP_WRITE, flags(), 0, fd, bufferAddress + pos, limit - pos, 0, extraData);
    }

    boolean addAccept(int fd, long address, long addressLength, short extraData) {
        return enqueueSqe(Native.IORING_OP_ACCEPT, flags(), Native.SOCK_NONBLOCK | Native.SOCK_CLOEXEC, fd,
                address, 0, addressLength, extraData);
    }

    //fill the address which is associated with server poll link user_data
    boolean addPollRemove(int fd, int pollMask, short extraData) {
        return enqueueSqe(Native.IORING_OP_POLL_REMOVE, 0, 0, fd,
                          encode(fd, Native.IORING_OP_POLL_ADD, (short) pollMask), 0, 0, extraData);
    }

    boolean addConnect(int fd, long socketAddress, long socketAddressLength, short extraData) {
        return enqueueSqe(Native.IORING_OP_CONNECT, flags(), 0, fd, socketAddress, 0, socketAddressLength, extraData);
    }

    boolean addWritev(int fd, long iovecArrayAddress, int length, short extraData) {
        return enqueueSqe(Native.IORING_OP_WRITEV, flags(), 0, fd, iovecArrayAddress, length, 0, extraData);
    }

    boolean addClose(int fd, short extraData) {
        return enqueueSqe(Native.IORING_OP_CLOSE, flags(), 0, fd, 0, 0, 0, extraData);
    }

    int submit() {
        int submit = tail - head;
        return submit > 0 ? submit(submit, 0, 0) : 0;
    }

    int submitAndWait() {
        int submit = tail - head;
        if (submit > 0) {
            return submit(submit, 1, Native.IORING_ENTER_GETEVENTS);
        }
        assert submit == 0;
        int ret = Native.ioUringEnter(ringFd, 0, 1, Native.IORING_ENTER_GETEVENTS);
        if (ret < 0) {
            throw new RuntimeException("ioUringEnter syscall returned " + ret);
        }
        return ret; // should be 0
    }

    private int submit(int toSubmit, int minComplete, int flags) {
        PlatformDependent.putIntOrdered(kTailAddress, tail); // release memory barrier
        int ret = Native.ioUringEnter(ringFd, toSubmit, minComplete, flags);
        head = PlatformDependent.getIntVolatile(kHeadAddress); // acquire memory barrier
        if (ret != toSubmit) {
            if (ret < 0) {
                throw new RuntimeException("ioUringEnter syscall returned " + ret);
            }
            logger.warn("Not all submissions succeeded");
        }
        submissionCallback.run();
        return ret;
    }

    private void setTimeout(long timeoutNanoSeconds) {
        long seconds, nanoSeconds;

        if (timeoutNanoSeconds == 0) {
            seconds = 0;
            nanoSeconds = 0;
        } else {
            seconds = (int) min(timeoutNanoSeconds / 1000000000L, Integer.MAX_VALUE);
            nanoSeconds = (int) max(timeoutNanoSeconds - seconds * 1000000000L, 0);
        }

        PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_SEC_FIELD, seconds);
        PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_NSEC_FIELD, nanoSeconds);
    }

    public long count() {
        return tail - head;
    }

    //delete memory
    public void release() {
        PlatformDependent.freeMemory(timeoutMemoryAddress);
    }
}
