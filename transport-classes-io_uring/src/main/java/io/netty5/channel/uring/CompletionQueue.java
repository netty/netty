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

import static io.netty5.channel.uring.UserData.decode;

/**
 * Completion queue implementation for io_uring.
 */
final class CompletionQueue implements IntSupplier {
    private static final Logger logger = LoggerFactory.getLogger(CompletionQueue.class);

    //these offsets are used to access specific properties
    //CQE (https://github.com/axboe/liburing/blob/master/src/include/liburing/io_uring.h#L162)
    private static final int CQE_USER_DATA_FIELD = 0;
    private static final int CQE_RES_FIELD = 8;
    private static final int CQE_FLAGS_FIELD = 12;

    private static final long CQE_SIZE = 16;

    //these unsigned integer pointers(shared with the kernel) will be changed by the kernel
    private final long kHeadAddress;
    private final long kTailAddress;

    private final long completionQueueArrayAddress;

    final int ringSize;
    final long ringAddress;
    final int ringFd;

    private final int ringEntries;
    private final int ringMask;
    private int ringHead;

    CompletionQueue(long kHeadAddress, long kTailAddress, long kRingMaskAddress, long kRingEntriesAddress,
                    long kOverflowAddress, long completionQueueArrayAddress, int ringSize, long ringAddress,
                    int ringFd) {
        this.kHeadAddress = kHeadAddress;
        this.kTailAddress = kTailAddress;
        this.completionQueueArrayAddress = completionQueueArrayAddress;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;

        ringEntries = PlatformDependent.getIntVolatile(kRingEntriesAddress);
        ringMask = PlatformDependent.getIntVolatile(kRingMaskAddress);
        ringHead = PlatformDependent.getIntVolatile(kHeadAddress);
    }

    /**
     * Returns {@code true} if any completion event is ready to be processed by
     * {@link #process(CompletionCallback)}, {@code false} otherwise.
     */
    boolean hasCompletions() {
        return ringHead != PlatformDependent.getIntVolatile(kTailAddress);
    }

    @Override
    public int getAsInt() {
        return count();
    }

    int count() {
        return PlatformDependent.getIntVolatile(kTailAddress) - ringHead;
    }

    /**
     * Process the completion events in the {@link CompletionQueue} and return the number of processed
     * events.
     */
    int process(CompletionCallback callback) {
        int tail = PlatformDependent.getIntVolatile(kTailAddress);
        int i = 0;
        while (ringHead != tail) {
            long cqeAddress = completionQueueArrayAddress + (ringHead & ringMask) * CQE_SIZE;

            long udata = PlatformDependent.getLong(cqeAddress + CQE_USER_DATA_FIELD);
            int res = PlatformDependent.getInt(cqeAddress + CQE_RES_FIELD);
            int flags = PlatformDependent.getInt(cqeAddress + CQE_FLAGS_FIELD);

            //Ensure that the kernel only sees the new value of the head index after the CQEs have been read.
            ringHead++;
            PlatformDependent.putIntOrdered(kHeadAddress, ringHead);

            i++;

            if (logger.isTraceEnabled()) {
                logger.trace("completed(ring {}): {}(id={}, res={})",
                        ringFd, Native.opToStr(UserData.decodeOp(udata)), UserData.decodeId(udata), res);
            }
            try {
                decode(res, flags, udata, callback);
            } catch (Error e) {
                throw e;
            } catch (Throwable throwable) {
                handleLoopException(throwable);
            }
        }
        return i;
    }

    private static void handleLoopException(Throwable throwable) {
        logger.warn("Unexpected exception in the IO event loop.", throwable);

        // Prevent possible consecutive immediate failures that lead to
        // excessive CPU consumption.
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(", ", "CompletionQueue [", "]");
        int tail = PlatformDependent.getIntVolatile(kTailAddress);
        int head = ringHead;
        while (head != tail) {
            long cqeAddress = completionQueueArrayAddress + (ringHead & ringMask) * CQE_SIZE;
            long udata = PlatformDependent.getLong(cqeAddress + CQE_USER_DATA_FIELD);
            int res = PlatformDependent.getInt(cqeAddress + CQE_RES_FIELD);
            sb.add(Native.opToStr(UserData.decodeOp(udata)) + "(id=" + UserData.decodeId(udata) + ",res=" + res + ')');
            head++;
        }
        return sb.toString();
    }

    /**
     * Block until there is at least one completion ready to be processed.
     */
    void ioUringWaitCqe() {
        int ret = Native.ioUringEnter(ringFd, 0, 1, Native.IORING_ENTER_GETEVENTS);
        if (logger.isTraceEnabled()) {
            logger.trace("completed(ring {}): {}", ringFd, this);
        }
        if (ret < 0) {
            throw new RuntimeException("ioUringEnter syscall returned " + ret);
        }
    }
}
