/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.util.internal.PlatformDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.StringJoiner;
import java.util.function.IntSupplier;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class SubmissionQueue {
    private static final Logger logger = LoggerFactory.getLogger(SubmissionQueue.class);

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
    private final long timeoutMemoryAddress;
    private final int iosqeAsyncThreshold;
    private final IntSupplier completionCount;
    private int numHandledFds;
    private boolean link;
    private int head;
    private int tail;

    SubmissionQueue(long kHeadAddress, long kTailAddress, long kRingMaskAddress, long kRingEntriesAddress,
                    long kFlagsAddress, long kDroppedAddress, long kArrayAddress,
                    long submissionQueueArrayAddress, int ringSize, long ringAddress, int ringFd,
                    int iosqeAsyncThreshold, IntSupplier completionCount) {
        this.kHeadAddress = kHeadAddress;
        this.kTailAddress = kTailAddress;
        this.kFlagsAddress = kFlagsAddress;
        this.kDroppedAddress = kDroppedAddress;
        this.kArrayAddress = kArrayAddress;
        this.submissionQueueArrayAddress = submissionQueueArrayAddress;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;
        this.ringEntries = PlatformDependent.getIntVolatile(kRingEntriesAddress);
        this.ringMask = PlatformDependent.getIntVolatile(kRingMaskAddress);
        this.head = PlatformDependent.getIntVolatile(kHeadAddress);
        this.tail = PlatformDependent.getIntVolatile(kTailAddress);

        this.timeoutMemoryAddress = PlatformDependent.allocateMemory(KERNEL_TIMESPEC_SIZE);
        this.iosqeAsyncThreshold = iosqeAsyncThreshold;
        this.completionCount = completionCount;

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

    void link(boolean link) {
        this.link = link;
    }

    private int flags() {
        return (numHandledFds < iosqeAsyncThreshold ? 0 : Native.IOSQE_ASYNC) | (link ? Native.IOSQE_LINK : 0);
    }

    long enqueueSqe(byte op, int flags, short ioPrio, int rwFlags, int fd,
                               long bufferAddress, int length, long offset, int id, short data) {
        int pending = tail - head;
        if (pending == ringEntries) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        long sqe = submissionQueueArrayAddress + (tail++ & ringMask) * SQE_SIZE;
        long udata = UserData.encode(id, op, data);
        setData(sqe, op, flags, ioPrio, rwFlags, fd, bufferAddress, length, offset, udata);
        return udata;
    }

    void enqueueSqe(byte op, int flags, short ioPrio, int rwFlags, int fd,
                    long bufferAddress, int length, long offset, long udata) {
        int pending = tail - head;
        if (pending == ringEntries) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        long sqe = submissionQueueArrayAddress + (tail++ & ringMask) * SQE_SIZE;
        setData(sqe, op, flags, ioPrio, rwFlags, fd, bufferAddress, length, offset, udata);
    }

    private void setData(long sqe, byte op, int flags, short ioPrio, int rwFlags, int fd, long bufferAddress,
                         int length, long offset, long udata) {
        //set sqe(submission queue) properties

        PlatformDependent.putByte(sqe + SQE_OP_CODE_FIELD, op);
        PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, (byte) flags);
        // This constant is set up-front
        PlatformDependent.putShort(sqe + SQE_IOPRIO_FIELD, ioPrio);
        PlatformDependent.putInt(sqe + SQE_FD_FIELD, fd);
        PlatformDependent.putLong(sqe + SQE_OFFSET_FIELD, offset);
        PlatformDependent.putLong(sqe + SQE_ADDRESS_FIELD, bufferAddress);
        PlatformDependent.putInt(sqe + SQE_LEN_FIELD, length);
        PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, rwFlags);
        PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, udata);

        if (logger.isTraceEnabled()) {
            if (op == Native.IORING_OP_WRITEV || op == Native.IORING_OP_READV) {
                logger.trace("add(ring {}): {}(fd={}, len={} ({} bytes), off={}, data={})",
                        ringFd, Native.opToStr(op), fd, length, Iov.sumSize(bufferAddress, length), offset, udata);
            } else {
                logger.trace("add(ring {}): {}(fd={}, len={}, off={}, data={})",
                        ringFd, Native.opToStr(op), fd, length, offset, udata);
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

    long addNop(int fd, int flags, int id, short data) {
        return enqueueSqe(Native.IORING_OP_NOP, flags, (short) 0, 0, fd, 0, 0, 0, id, data);
    }

    long addTimeout(int fd, long nanoSeconds, int id, short extraData) {
        setTimeout(nanoSeconds);
        return enqueueSqe(Native.IORING_OP_TIMEOUT, 0, (short) 0, 0, fd,
                timeoutMemoryAddress, 1, 0, id, extraData);
    }

    long addLinkTimeout(int fd, long nanoSeconds, int id, short extraData) {
        setTimeout(nanoSeconds);
        return enqueueSqe(Native.IORING_OP_LINK_TIMEOUT, 0, (short) 0, 0, fd,
                timeoutMemoryAddress, 1, 0, id, extraData);
    }

    long addEventFdRead(int fd, long bufferAddress, int pos, int limit, int id, short extraData) {
        return enqueueSqe(Native.IORING_OP_READ, 0, (short) 0, 0, fd,
                bufferAddress + pos, limit - pos, 0, id, extraData);
    }

    long addCancel(int fd, long sqeToCancel, int id) {
        return enqueueSqe(Native.IORING_OP_ASYNC_CANCEL, 0, (short) 0, 0, fd, sqeToCancel, 0, 0, id, (short) 0);
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
        if (logger.isTraceEnabled()) {
            logger.trace("submit(ring {}): {}", ringFd, toString());
        }
        PlatformDependent.putIntOrdered(kTailAddress, tail); // release memory barrier
        int ret = Native.ioUringEnter(ringFd, toSubmit, minComplete, flags);
        head = PlatformDependent.getIntVolatile(kHeadAddress); // acquire memory barrier
        if (ret != toSubmit) {
            if (ret < 0) {
                throw new RuntimeException("ioUringEnter syscall returned " + ret);
            }
            logger.warn("Not all submissions succeeded. Only {} of {} SQEs were submitted, " +
                    "while there are {} pending completions.", ret, toSubmit, completionCount.getAsInt());
        }
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
