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

import io.netty.channel.unix.Buffer;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.StringJoiner;

final class SubmissionQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SubmissionQueue.class);

    static final int SQE_SIZE = 64;

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

    // These unsigned integer pointers(shared with the kernel) will be changed by the kernel and us
    // using a VarHandle.
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());
    private final ByteBuffer kHead;
    private final ByteBuffer kTail;
    private final ByteBuffer submissionQueueArray;

    final int ringEntries;
    private final int ringMask; // = ringEntries - 1

    final int ringSize;
    final long ringAddress;
    final int ringFd;
    int enterRingFd;
    private int enterFlags;
    private int head;
    private int tail;

    private boolean closed;

    SubmissionQueue(ByteBuffer khead, ByteBuffer ktail, int ringMask, int ringEntries, ByteBuffer submissionQueueArray,
                    int ringSize, long ringAddress,
                    int ringFd) {
        this.kHead = khead;
        this.kTail = ktail;
        this.submissionQueueArray = submissionQueueArray;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;
        this.enterRingFd = ringFd;
        this.ringEntries = ringEntries;
        this.ringMask = ringMask;
        this.head = (int) INT_HANDLE.getVolatile(khead, 0);
        this.tail = (int) INT_HANDLE.getVolatile(ktail, 0);
    }

    long submissionQueueArrayAddress() {
        return Buffer.memoryAddress(submissionQueueArray);
    }

    void close() {
        closed = true;
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException();
        }
    }

    void tryRegisterRingFd() {
        checkClosed();
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

    long enqueueSqe(byte opcode, byte flags, short ioPrio, int fd, long union1, long union2, int len,
                             int union3, long udata, short union4, short personality, int union5, long union6) {
        checkClosed();
        int pending = tail - head;
        if (pending == ringEntries) {
            int submitted = submit();
            if (submitted == 0) {
                // We have a problem, could not submit to make more room in the ring
                throw new RuntimeException("SQ ring full and no submissions accepted");
            }
        }
        int sqe = sqeIndex(tail++, ringMask);

        //set sqe(submission queue) properties
        submissionQueueArray.put(sqe + SQE_OP_CODE_FIELD, opcode);
        submissionQueueArray.put(sqe + SQE_FLAGS_FIELD, flags);
        // This constant is set up-front
        submissionQueueArray.putShort(sqe + SQE_IOPRIO_FIELD, ioPrio);
        submissionQueueArray.putInt(sqe + SQE_FD_FIELD, fd);
        submissionQueueArray.putLong(sqe + SQE_UNION1_FIELD, union1);
        submissionQueueArray.putLong(sqe + SQE_UNION2_FIELD, union2);
        submissionQueueArray.putInt(sqe + SQE_LEN_FIELD, len);
        submissionQueueArray.putInt(sqe + SQE_UNION3_FIELD, union3);
        submissionQueueArray.putLong(sqe + SQE_USER_DATA_FIELD, udata);
        submissionQueueArray.putShort(sqe + SQE_UNION4_FIELD, union4);
        submissionQueueArray.putShort(sqe + SQE_PERSONALITY_FIELD, personality);
        submissionQueueArray.putInt(sqe + SQE_UNION5_FIELD, union5);
        submissionQueueArray.putLong(sqe + SQE_UNION6_FIELD, union6);

        if (logger.isTraceEnabled()) {
            if (opcode == Native.IORING_OP_WRITEV || opcode == Native.IORING_OP_READV) {
                logger.trace("add(ring={}, enterRing:{} ): {}(fd={}, len={}, off={}, data={})",
                        ringFd, enterRingFd, Native.opToStr(opcode), fd, len, union1, udata);
            } else {
                logger.trace("add(ring={}, enterRing:{}): {}(fd={}, len={}, off={}, data={})",
                        ringFd, enterRingFd, Native.opToStr(opcode), fd, len, union1, udata);
            }
        }
        return udata;
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(", ", "SubmissionQueue [", "]");
        if (closed) {
            sb.add("closed");
        } else {
            int pending = tail - head;
            int idx = tail;
            for (int i = 0; i < pending; i++) {
                int sqe = sqeIndex(idx++, ringMask);
                sb.add(Native.opToStr(submissionQueueArray.get(sqe + SQE_OP_CODE_FIELD)) +
                        "(fd=" + submissionQueueArray.getInt(sqe + SQE_FD_FIELD) + ')');
            }
        }
        return sb.toString();
    }

    private static int sqeIndex(int tail, int ringMask) {
        return (tail & ringMask) * SQE_SIZE;
    }

    long addNop(byte flags, long udata) {
        // Mimic what liburing does:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L592
        return enqueueSqe(Native.IORING_OP_NOP, flags, (short) 0, -1, 0, 0, 0, 0, udata,
                (short) 0, (short) 0, 0, 0);
    }

    long addTimeout(long timeoutMemoryAddress, long udata) {
        // Mimic what liburing does. We want to use a count of 1:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L599
        return enqueueSqe(Native.IORING_OP_TIMEOUT, (byte) 0, (short) 0, -1, 1, timeoutMemoryAddress, 1,
                0, udata, (short) 0, (short) 0, 0, 0);
    }

    long addLinkTimeout(long timeoutMemoryAddress, long extraData) {
        // Mimic what liburing does:
        // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L687
        return enqueueSqe(Native.IORING_OP_LINK_TIMEOUT, (byte) 0, (short) 0, -1, 1, timeoutMemoryAddress, 1,
                0, extraData, (short) 0, (short) 0, 0, 0);
    }

    long addEventFdRead(int fd, long bufferAddress, int pos, int limit, long udata) {
        return enqueueSqe(Native.IORING_OP_READ, (byte) 0, (short) 0, fd, 0, bufferAddress + pos, limit - pos,
                0, udata, (short) 0, (short) 0, 0, 0);
    }

    // Mimic what liburing does:
    // https://github.com/axboe/liburing/blob/liburing-2.8/src/include/liburing.h#L673
    long addCancel(long sqeToCancel, long udata) {
        return enqueueSqe(Native.IORING_OP_ASYNC_CANCEL, (byte) 0, (short) 0, -1, 0, sqeToCancel, 0, 0,
                udata, (short) 0, (short) 0, 0, 0);
    }

    int submit() {
        checkClosed();
        int submit = tail - head;
        return submit > 0 ? submit(submit, 0, 0) : 0;
    }

    int submitAndWait() {
        checkClosed();
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
        INT_HANDLE.setRelease(kTail, 0, tail);
        int ret = ioUringEnter(toSubmit, minComplete, flags);
        head = (int) INT_HANDLE.getVolatile(kHead, 0); // acquire memory barrier
        if (ret != toSubmit) {
            if (ret < 0) {
                throw new RuntimeException("ioUringEnter syscall returned " + ret);
            }
        }
        return ret;
    }

    private int ioUringEnter(int toSubmit, int minComplete, int flags) {
        int f = enterFlags | flags;

        if (IoUring.isSetupSubmitAllSupported()) {
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

    public int count() {
        return tail - head;
    }

    public int remaining() {
        return ringEntries - count();
    }
}
