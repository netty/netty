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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.StringJoiner;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class SubmissionQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SubmissionQueue.class);

    private static final long SQE_SIZE = 64;
    private static final int INT_SIZE = Integer.BYTES; //no 32 Bit support?
    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    //these offsets are used to access specific properties
    //SQE https://github.com/axboe/liburing/blob/liburing-2.6/src/include/liburing/io_uring.h#L30
    private static final int SQE_OP_CODE_FIELD = 0;
    private static final int SQE_FLAGS_FIELD = 1;
    private static final int SQE_IOPRIO_FIELD = 2; // u16
    private static final int SQE_FD_FIELD = 4; // s32
    private static final int SQE_UNION1_FIELD = 8;
    private static final int SQE_UNION2_FIELD = 16;
    private static final int SQE_LEN_FIELD = 24;
    private static final int SQE_UNION3_FIELD = 28;
    private static final int SQE_USER_DATA_FIELD = 32;
    private static final int SQE_UNION4_FIELD = 40;
    private static final int SQE_PERSONALITY_FIELD = 42;
    private static final int SQE_UNION5_FIELD = 44;
    private static final int SQE_UNION6_FIELD = 48;

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
    int enterRingFd;
    private int enterFlags;
    private final long timeoutMemoryAddress;
    private int head;
    private int tail;

    SubmissionQueue(long kHeadAddress, long kTailAddress, long kRingMaskAddress, long kRingEntriesAddress,
                    long kFlagsAddress, long kDroppedAddress, long kArrayAddress,
                    long submissionQueueArrayAddress, int ringSize, long ringAddress,
                    int ringFd) {
        this.kHeadAddress = kHeadAddress;
        this.kTailAddress = kTailAddress;
        this.kFlagsAddress = kFlagsAddress;
        this.kDroppedAddress = kDroppedAddress;
        this.kArrayAddress = kArrayAddress;
        this.submissionQueueArrayAddress = submissionQueueArrayAddress;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;
        this.enterRingFd = ringFd;
        this.ringEntries = PlatformDependent.getIntVolatile(kRingEntriesAddress);
        this.ringMask = PlatformDependent.getIntVolatile(kRingMaskAddress);
        this.head = PlatformDependent.getIntVolatile(kHeadAddress);
        this.tail = PlatformDependent.getIntVolatile(kTailAddress);

        this.timeoutMemoryAddress = PlatformDependent.allocateMemory(KERNEL_TIMESPEC_SIZE);

        // Zero the whole SQE array first
        PlatformDependent.setMemory(submissionQueueArrayAddress, ringEntries * SQE_SIZE, (byte) 0);

        // Fill SQ array indices (1-1 with SQE array) and set nonzero constant SQE fields
        long address = kArrayAddress;
        for (int i = 0; i < ringEntries; i++, address += INT_SIZE) {
            PlatformDependent.putInt(address, i);
        }
    }

    void tryRegisterRingFd() {
        // Try to use IORING_REGISTER_RING_FDS.
        // See https://manpages.debian.org/unstable/liburing-dev/io_uring_register.2.en.html#IORING_REGISTER_RING_FDS
        int enterRingFd = Native.ioUringRegisterRingFds(ringFd);
        final int enterFlags;
        if (enterRingFd < 0) {
            // Use of IORING_REGISTER_RING_FDS failed, just use the ring fd directly.
            enterRingFd = ringFd;
            enterFlags = 0;
        } else {
            enterFlags = Native.IORING_ENTER_REGISTERED_RING;
        }
        this.enterRingFd = enterRingFd;
        this.enterFlags = enterFlags;
    }

    private long enqueueSqe0(byte opcode, byte flags, short ioPrio, int fd, long union1, long union2, int len,
                             int union3, long udata, short union4, short personality, int union5, long union6) {
        int pending = tail - head;
        if (pending == ringEntries) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        long sqe = submissionQueueArrayAddress + (tail++ & ringMask) * SQE_SIZE;
        setData(sqe, opcode, flags, ioPrio, fd, union1, union2, len,
                union3, udata, union4, personality, union5, union6);
        return udata;
    }

    void enqueueSqe(byte opcode, byte flags, short ioPrio, int fd, long union1, long union2, int len, int union3,
                    long udata, short union4, short personality, int union5, long union6) {
        int pending = tail - head;
        if (pending == ringEntries) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        long sqe = submissionQueueArrayAddress + (tail++ & ringMask) * SQE_SIZE;
        setData(sqe, opcode, flags, ioPrio, fd, union1, union2, len,
                union3, udata, union4, personality, union5, union6);
    }

    private void setData(long sqe, byte opcode, byte flags, short ioPrio, int fd, long union1, long union2, int len,
                         int union3, long udata, short union4, short personality, int union5, long union6) {
        //set sqe(submission queue) properties

        PlatformDependent.putByte(sqe + SQE_OP_CODE_FIELD, opcode);
        PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, flags);
        // This constant is set up-front
        PlatformDependent.putShort(sqe + SQE_IOPRIO_FIELD, ioPrio);
        PlatformDependent.putInt(sqe + SQE_FD_FIELD, fd);
        PlatformDependent.putLong(sqe + SQE_UNION1_FIELD, union1);
        PlatformDependent.putLong(sqe + SQE_UNION2_FIELD, union2);
        PlatformDependent.putInt(sqe + SQE_LEN_FIELD, len);
        PlatformDependent.putInt(sqe + SQE_UNION3_FIELD, union3);
        PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, udata);
        PlatformDependent.putShort(sqe + SQE_UNION4_FIELD, union4);
        PlatformDependent.putShort(sqe + SQE_PERSONALITY_FIELD, personality);
        PlatformDependent.putInt(sqe + SQE_UNION5_FIELD, union5);
        PlatformDependent.putLong(sqe + SQE_UNION6_FIELD, union6);

        if (logger.isTraceEnabled()) {
            if (opcode == Native.IORING_OP_WRITEV || opcode == Native.IORING_OP_READV) {
                logger.trace("add(ring={}, enterRing:{} ): {}(fd={}, len={} ({} bytes), off={}, data={})",
                        ringFd, enterRingFd, Native.opToStr(opcode), fd, len, Iov.sumSize(union2, len), union1, udata);
            } else {
                logger.trace("add(ring={}, enterRing:{}): {}(fd={}, len={}, off={}, data={})",
                        ringFd, enterRingFd, Native.opToStr(opcode), fd, len, union1, udata);
            }
        }
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(", ", "SubmissionQueue [", "]");
        int pending = tail - head;
        for (int i = 0; i < pending; i++) {
            long sqe = submissionQueueArrayAddress + (head + i & ringMask) * SQE_SIZE;
            sb.add(Native.opToStr(PlatformDependent.getByte(sqe + SQE_OP_CODE_FIELD)) +
                    "(fd=" + PlatformDependent.getInt(sqe + SQE_FD_FIELD) + ')');
        }
        return sb.toString();
    }

    long addNop(byte flags, long udata) {
        // Mimic what liburing does:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L592
        return enqueueSqe0(Native.IORING_OP_NOP, flags, (short) 0, -1, 0, 0, 0, 0, udata,
                (short) 0, (short) 0, 0, 0);
    }

    long addTimeout(long nanoSeconds, long udata) {
        setTimeout(nanoSeconds);
        // Mimic what liburing does. We want to use a count of 1:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L599
        return enqueueSqe0(Native.IORING_OP_TIMEOUT, (byte) 0, (short) 0, -1, 1, timeoutMemoryAddress, 1,
                0, udata, (short) 0, (short) 0, 0, 0);
    }

    long addLinkTimeout(long nanoSeconds, long extraData) {
        setTimeout(nanoSeconds);
        // Mimic what liburing does:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L687
        return enqueueSqe0(Native.IORING_OP_LINK_TIMEOUT, (byte) 0, (short) 0, -1, 1, timeoutMemoryAddress, 1,
                0, extraData, (short) 0, (short) 0, 0, 0);
    }

    long addEventFdRead(int fd, long bufferAddress, int pos, int limit, long udata) {
        return enqueueSqe0(Native.IORING_OP_READ, (byte) 0, (short) 0, fd, 0, bufferAddress + pos, limit - pos,
                0, udata, (short) 0, (short) 0, 0, 0);
    }

    // Mimic what liburing does:
    // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L673
    long addCancel(long sqeToCancel, long udata) {
        return enqueueSqe0(Native.IORING_OP_ASYNC_CANCEL, (byte) 0, (short) 0, -1, 0, sqeToCancel, 0, 0,
                udata, (short) 0, (short) 0, 0, 0);
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
        int ret = ioUringEnter(0, 1, Native.IORING_ENTER_GETEVENTS);
        if (ret < 0) {
            throw new RuntimeException("ioUringEnter syscall returned " + ret);
        }
        return ret; // should be 0
    }

    private int submit(int toSubmit, int minComplete, int flags) {
        PlatformDependent.putIntOrdered(kTailAddress, tail); // release memory barrier
        int ret = ioUringEnter(toSubmit, minComplete, flags);
        head = PlatformDependent.getIntVolatile(kHeadAddress); // acquire memory barrier
        if (ret != toSubmit) {
            if (ret < 0) {
                throw new RuntimeException("ioUringEnter syscall returned " + ret);
            }
        }
        return ret;
    }

    private int ioUringEnter(int toSubmit, int minComplete, int flags) {
        int f = enterFlags | flags;

        if (IoUring.isIOUringSetupSubmitAllSupported()) {
            return ioUringEnter0(toSubmit, minComplete, f);
        }
        // If IORING_SETUP_SUBMIT_ALL is not supported we need to loop until we submitted everything as
        // io_uring_enter(...) will stop submitting once the first inline executed submission fails.
        int submitted = 0;
        for (;;) {
            int ret = ioUringEnter0(toSubmit, minComplete, f);
            if (ret < 0) {
                return ret;
            }
            submitted += ret;
            if (ret == toSubmit) {
                return submitted;
            }
            if (logger.isTraceEnabled()) {
                // some submission might fail if these are done inline and failed.
                logger.trace("Not all submissions succeeded. Only {} of {} SQEs were submitted.", ret, toSubmit);
            }
            toSubmit -= ret;
        }
    }

    private int ioUringEnter0(int toSubmit, int minComplete, int f) {
        if (logger.isTraceEnabled()) {
            logger.trace("io_uring_enter(ring={}, enterRing={}, toSubmit={}, minComplete={}, flags={}): {}",
                    ringFd, enterRingFd, toSubmit, minComplete, f, toString());
        }
        return Native.ioUringEnter(enterRingFd, toSubmit, minComplete, f);
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

    public int count() {
        return tail - head;
    }

    public int remaining() {
        return ringEntries - count();
    }

    //delete memory
    public void release() {
        PlatformDependent.freeMemory(timeoutMemoryAddress);
    }
}
