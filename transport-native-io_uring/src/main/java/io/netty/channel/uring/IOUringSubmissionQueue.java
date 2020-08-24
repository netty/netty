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

import java.nio.ByteBuffer;

final class IOUringSubmissionQueue {

    private static final int SQE_SIZE = 64;
    private static final int INT_SIZE = Integer.BYTES; //no 32 Bit support?
    private static final int KERNEL_TIMESPEC_SIZE = 16; //__kernel_timespec
    private static final int POLLIN = 1;
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
        long sqe = 0;
        if ((next - sqeHead) <= kRingEntries) {
            long index = sqeTail & toUnsignedLong(PlatformDependent.getInt(kRingMaskAddress));
            sqe = SQE_SIZE * index + submissionQueueArrayAddress;
            sqeTail = next;
        }
        return sqe;
    }

    private void setData(long sqe, long eventId, EventType type, int fd, long bufferAddress, int length, long offset) {
        //Todo cleaner
        //set sqe(submission queue) properties
        PlatformDependent.putByte(sqe + SQE_OP_CODE_FIELD, (byte) type.getOp());
        PlatformDependent.putShort(sqe + SQE_IOPRIO_FIELD, (short) 0);
        PlatformDependent.putInt(sqe + SQE_FD_FIELD, fd);
        PlatformDependent.putLong(sqe + SQE_OFFSET_FIELD, offset);
        PlatformDependent.putLong(sqe + SQE_ADDRESS_FIELD, bufferAddress);
        PlatformDependent.putInt(sqe + SQE_LEN_FIELD, length);
        PlatformDependent.putLong(sqe + SQE_USER_DATA_FIELD, eventId);

        //poll<link>read or accept operation
        if (type == EventType.POLL_LINK) {
            PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, (byte) IOSQE_IO_LINK);
        } else {
           PlatformDependent.putByte(sqe + SQE_FLAGS_FIELD, (byte) 0);
        }

        //c union set Rw-Flags or accept_flags
        if (type != EventType.ACCEPT) {
            PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, 0);
        } else {
            //accept_flags set NON_BLOCKING
            PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, SOCK_NONBLOCK | SOCK_CLOEXEC);
        }

        // pad field array -> all fields should be zero
        long offsetIndex = 0;
        for (int i = 0; i < 3; i++) {
            PlatformDependent.putLong(sqe + SQE_PAD_FIELD + offsetIndex, 0);
            offsetIndex += 8;
        }

        System.out.println("OPField: " + PlatformDependent.getByte(sqe + SQE_OP_CODE_FIELD));
        System.out.println("UserDataField: " + PlatformDependent.getLong(sqe + SQE_USER_DATA_FIELD));
        System.out.println("BufferAddress: " + PlatformDependent.getLong(sqe + SQE_ADDRESS_FIELD));
        System.out.println("Length: " + PlatformDependent.getInt(sqe + SQE_LEN_FIELD));
        System.out.println("Offset: " + PlatformDependent.getLong(sqe + SQE_OFFSET_FIELD));
    }

    public boolean addTimeout(long nanoSeconds, long eventId) {
        long sqe = getSqe();
        if (sqe == 0) {
            return false;
        }
        setTimeout(nanoSeconds);
        setData(sqe, eventId, EventType.TIMEOUT, -1, timeoutMemoryAddress, 1, 0);
        return true;
    }

    public boolean addPoll(long eventId, int fd, EventType eventType) {
        long sqe = getSqe();
        if (sqe == 0) {
            return false;
        }
        setData(sqe, eventId, eventType, fd, 0, 0, 0);
        PlatformDependent.putInt(sqe + SQE_RW_FLAGS_FIELD, POLLIN);
        return true;
    }

    //Todo ring buffer errors for example if submission queue is full
    public boolean add(long eventId, EventType type, int fd, long bufferAddress, int pos, int limit) {
        long sqe = getSqe();
        if (sqe == 0) {
            return false;
        }
        System.out.println("fd " + fd);
        System.out.println("BufferAddress + pos: " + (bufferAddress + pos));
        System.out.println("limit + pos " + (limit - pos));
        setData(sqe, eventId, type, fd, bufferAddress + pos, limit - pos, 0);
        return true;
    }

    private int flushSqe() {
        long kTail = toUnsignedLong(PlatformDependent.getInt(kTailAddress));
        long kHead = toUnsignedLong(PlatformDependent.getIntVolatile(kHeadAddress));
        long kRingMask = toUnsignedLong(PlatformDependent.getInt(kRingMaskAddress));

        System.out.println("Ktail: " + kTail);
        System.out.println("Ktail: " + kHead);
        System.out.println("SqeHead: " + sqeHead);
        System.out.println("SqeTail: " + sqeTail);

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
        System.out.println("Submitted: " + submitted);

        int ret = Native.ioUringEnter(ringFd, submitted, 0, 0);
        if (ret < 0) {
            throw new RuntimeException("ioUringEnter syscall");
        }
    }

    private void setTimeout(long timeoutNanoSeconds) {
        long seconds, nanoSeconds;

        //Todo
        if (timeoutNanoSeconds == 0) {
            seconds = 0;
            nanoSeconds = 0;
        } else {
            seconds = timeoutNanoSeconds / 1000000000L;
            nanoSeconds = timeoutNanoSeconds % 1000;
        }

        PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_SEC_FIELD, seconds);
        PlatformDependent.putLong(timeoutMemoryAddress + KERNEL_TIMESPEC_TV_NSEC_FIELD, nanoSeconds);
    }

    //delete memory
    public void release() {
        Buffer.free(timeoutMemory);
    }

    public void setSqeHead(long sqeHead) {
        this.sqeHead = sqeHead;
    }

    public void setSqeTail(long sqeTail) {
        this.sqeTail = sqeTail;
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

    public long getSqeHead() {
        return this.sqeHead;
    }

    public int getRingFd() {
        return ringFd;
    }

    public long getSqeTail() {
        return this.sqeTail;
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
