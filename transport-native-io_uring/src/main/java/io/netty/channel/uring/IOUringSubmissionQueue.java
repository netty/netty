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

import io.netty.channel.unix.Buffer;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;

import static java.lang.Math.max;
import static java.lang.Math.min;

final class IOUringSubmissionQueue {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IOUringSubmissionQueue.class);

    private static final int SQE_SIZE = 64;
    private static final int INT_SIZE = Integer.BYTES; //no 32 Bit support?
    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec

    private static final int IOSQE_IO_LINK = 4;

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
    private final long kRingMaskAddress;
    private final long kRingEntriesAddress;
    private final long fFlagsAdress;
    private final long kDroppedAddress;
    private final long arrayAddress;

    private final long submissionQueueArrayAddress;

    private long sqeHead;
    private long sqeTail;

    private final int ringSize;
    private final long ringAddress;
    private final int ringFd;

    private static final int SOCK_NONBLOCK = 2048;
    private static final int SOCK_CLOEXEC = 524288;

    private final ByteBuffer timeoutMemory;
    private final long timeoutMemoryAddress;

    //private int sqeSubmitCounter;

    IOUringSubmissionQueue(long kHeadAddress, long kTailAddress, long kRingMaskAddress, long kRingEntriesAddress,
                           long fFlagsAdress, long kDroppedAddress, long arrayAddress,
                           long submissionQueueArrayAddress, int ringSize,
                           long ringAddress, int ringFd) {
        this.kHeadAddress = kHeadAddress;
        this.kTailAddress = kTailAddress;
        this.kRingMaskAddress = kRingMaskAddress;
        this.kRingEntriesAddress = kRingEntriesAddress;
        this.fFlagsAdress = fFlagsAdress;
        this.kDroppedAddress = kDroppedAddress;
        this.arrayAddress = arrayAddress;
        this.submissionQueueArrayAddress = submissionQueueArrayAddress;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;

        timeoutMemory = Buffer.allocateDirectWithNativeOrder(KERNEL_TIMESPEC_SIZE);
        timeoutMemoryAddress = Buffer.memoryAddress(timeoutMemory);
    }

    public long getSqe() {
        long next = sqeTail + 1;
        long kRingEntries = toUnsignedLong(PlatformDependent.getInt(kRingEntriesAddress));

        //acquire memory barrier
        long kHead = toUnsignedLong(PlatformDependent.getIntVolatile(kHeadAddress));

        long sqe = 0;
        if ((next - kHead) <= kRingEntries) {
            long index = sqeTail & toUnsignedLong(PlatformDependent.getInt(kRingMaskAddress));
            sqe = SQE_SIZE * index + submissionQueueArrayAddress;
            sqeTail = next;
        }
        return sqe;
    }

    private void setData(long sqe, byte op, int pollMask, int fd, long bufferAddress, int length, long offset) {
        //Todo cleaner
        //set sqe(submission queue) properties
        PlatformDependent.putByte(sqe + SQE_OP_CODE_FIELD, op);
        PlatformDependent.putShort(sqe + SQE_IOPRIO_FIELD, (short) 0);
        PlatformDependent.putInt(sqe + SQE_FD_FIELD, fd);
        PlatformDependent.putLong(sqe + SQE_OFFSET_FIELD, offset);
        PlatformDependent.putLong(sqe + SQE_ADDRESS_FIELD, bufferAddress);
        PlatformDependent.putInt(sqe + SQE_LEN_FIELD, length);

        //user_data should be same as POLL_LINK fd
        if (op == IOUring.OP_POLL_REMOVE) {
            PlatformDependent.putInt(sqe + SQE_FD_FIELD, -1);
            long uData = convertToUserData((byte) IOUring.IO_POLL, fd, pollMask);
            PlatformDependent.putLong(sqe + SQE_ADDRESS_FIELD, uData);
            PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, convertToUserData(op, fd, 0));
            PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, 0);
        } else {
            long uData = convertToUserData(op, fd, pollMask);
            PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, uData);
            //c union set Rw-Flags or accept_flags
            if (op != IOUring.OP_ACCEPT) {
                PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, pollMask);
            } else {
                //accept_flags set NON_BLOCKING
                PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, SOCK_NONBLOCK | SOCK_CLOEXEC);
            }
        }

        PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, (byte) 0);



        // pad field array -> all fields should be zero
        long offsetIndex = 0;
        for (int i = 0; i < 3; i++) {
            PlatformDependent.putLong(sqe + SQE_PAD_FIELD + offsetIndex, 0);
            offsetIndex += 8;
        }

        logger.trace("UserDataField: {}", PlatformDependent.getLong(sqe + SQE_USER_DATA_FIELD));
        logger.trace("BufferAddress: {}", PlatformDependent.getLong(sqe + SQE_ADDRESS_FIELD));
        logger.trace("Length: {}", PlatformDependent.getInt(sqe + SQE_LEN_FIELD));
        logger.trace("Offset: {}", PlatformDependent.getLong(sqe + SQE_OFFSET_FIELD));
    }

    public boolean addTimeout(long nanoSeconds) {
        long sqe = getSqe();
        if (sqe == 0) {
            return false;
        }
        setTimeout(nanoSeconds);
        setData(sqe, (byte) IOUring.IO_TIMEOUT, 0, -1, timeoutMemoryAddress, 1, 0);
        return true;
    }

    public boolean addPollIn(int fd) {
        return addPoll(fd, IOUring.POLLMASK_IN);
    }

    public boolean addPollRdHup(int fd) {
        return addPoll(fd, IOUring.POLLMASK_RDHUP);
    }

    public boolean addPollOut(int fd) {
        return addPoll(fd, IOUring.POLLMASK_OUT);
    }

    private boolean addPoll(int fd, int pollMask) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }

        setData(sqe, (byte) IOUring.IO_POLL, pollMask, fd, 0, 0, 0);
        return submitted;
    }

    //return true -> submit() was called
    public boolean addRead(int fd, long bufferAddress, int pos, int limit) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_READ, 0, fd, bufferAddress + pos, limit - pos, 0);
        return submitted;
    }

    public boolean addWrite(int fd, long bufferAddress, int pos, int limit) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_WRITE, 0, fd, bufferAddress + pos, limit - pos, 0);
        return submitted;
    }

    public boolean addAccept(int fd) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_ACCEPT, 0, fd, 0, 0, 0);
        return submitted;
    }

    //fill the address which is associated with server poll link user_data
    public boolean addPollRemove(int fd, int pollMask) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_POLL_REMOVE, pollMask, fd, 0, 0, 0);

        return submitted;
    }

    public boolean addConnect(int fd, long socketAddress, long socketAddressLength) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_CONNECT, 0, fd, socketAddress, 0, socketAddressLength);

        return submitted;
    }

    public boolean addWritev(int fd, long iovecArrayAddress, int length) {
        long sqe = 0;
        boolean submitted = false;
        while (sqe == 0) {
            sqe = getSqe();

            if (sqe == 0) {
                submit();
                submitted = true;
            }
        }
        setData(sqe, (byte) IOUring.OP_WRITEV, 0, fd, iovecArrayAddress, length, 0);

        return submitted;
    }

    private int flushSqe() {
        long kTail = toUnsignedLong(PlatformDependent.getInt(kTailAddress));
        long kHead = toUnsignedLong(PlatformDependent.getIntVolatile(kHeadAddress));
        long kRingMask = toUnsignedLong(PlatformDependent.getInt(kRingMaskAddress));

        logger.trace("Ktail: {}", kTail);
        logger.trace("Ktail: {}", kHead);
        logger.trace("SqeHead: {}", sqeHead);
        logger.trace("SqeTail: {}", sqeTail);

        if (sqeHead == sqeTail) {
            return (int) (kTail - kHead);
        }

        long toSubmit = sqeTail - sqeHead;
        while (toSubmit > 0) {
            long index = kTail & kRingMask;

            PlatformDependent.putInt(arrayAddress + index * INT_SIZE, (int) (sqeHead & kRingMask));

            sqeHead++;
            kTail++;
            toSubmit--;
        }

        //release
        PlatformDependent.putIntOrdered(kTailAddress, (int) kTail);

        return (int) (kTail - kHead);
    }

    public void submit() {
        int submitted = flushSqe();
        logger.trace("Submitted: {}", submitted);
        if (submitted > 0) {
            int ret = Native.ioUringEnter(ringFd, submitted, 0, 0);
            if (ret < 0) {
                throw new RuntimeException("ioUringEnter syscall");
            }
        }
    }
    private void setTimeout(long timeoutNanoSeconds) {
        long seconds, nanoSeconds;

        //Todo
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

    private long convertToUserData(byte op, int fd, int pollMask) {
        int opMask = (((short) op) << 16) | (((short) pollMask) & 0xFFFF);
        return (long) fd << 32 | opMask & 0xFFFFFFFFL;
    }

    public long count() {
        return (sqeTail - toUnsignedLong(PlatformDependent.getIntVolatile(kHeadAddress)));
    }

    //delete memory
    public void release() {
        Buffer.free(timeoutMemory);
    }

    public long getKHeadAddress() {
        return this.kHeadAddress;
    }

    public long getKTailAddress() {
        return this.kTailAddress;
    }

    public long getKRingMaskAddress() {
        return this.kRingMaskAddress;
    }

    public long getKRingEntriesAddress() {
        return this.kRingEntriesAddress;
    }

    public long getFFlagsAdress() {
        return this.fFlagsAdress;
    }

    public long getKDroppedAddress() {
        return this.kDroppedAddress;
    }

    public long getArrayAddress() {
        return this.arrayAddress;
    }

    public long getSubmissionQueueArrayAddress() {
        return this.submissionQueueArrayAddress;
    }

    public int getRingFd() {
        return ringFd;
    }

    public int getRingSize() {
        return this.ringSize;
    }

    public long getRingAddress() {
        return this.ringAddress;
    }

    //Todo Integer.toUnsignedLong -> maven checkstyle error
    public static long toUnsignedLong(int x) {
        return ((long) x) & 0xffffffffL;
    }

}
