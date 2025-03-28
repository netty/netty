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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.StringJoiner;

/**
 * Completion queue implementation for io_uring.
 */
final class CompletionQueue {
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    //these offsets are used to access specific properties
    //CQE (https://github.com/axboe/liburing/blob/master/src/include/liburing/io_uring.h#L162)
    private static final int CQE_USER_DATA_FIELD = 0;
    private static final int CQE_RES_FIELD = 8;
    private static final int CQE_FLAGS_FIELD = 12;

    static final int CQE_SIZE = 16;

    //these unsigned integer pointers(shared with the kernel) will be changed by the kernel and us
    // using a VarHandle.
    private final ByteBuffer khead;
    private final ByteBuffer ktail;
    private final ByteBuffer completionQueueArray;

    final int ringSize;
    final long ringAddress;
    final int ringFd;
    final int ringEntries;
    final int ringCapacity;

    private final int ringMask;
    private int ringHead;
    private boolean closed;

    CompletionQueue(ByteBuffer kHead, ByteBuffer kTail, int ringMask, int ringEntries,
                    ByteBuffer completionQueueArray, int ringSize, long ringAddress,
                    int ringFd, int ringCapacity) {
        this.khead = kHead;
        this.ktail = kTail;
        this.completionQueueArray = completionQueueArray;
        this.ringSize = ringSize;
        this.ringAddress = ringAddress;
        this.ringFd = ringFd;
        this.ringCapacity = ringCapacity;

        this.ringEntries = ringEntries;
        this.ringMask = ringMask;
        ringHead = (int) INT_HANDLE.getVolatile(kHead, 0);
    }

    void close() {
        closed = true;
    }

    /**
     * Returns {@code true} if any completion event is ready to be processed by
     * {@link #process(CompletionCallback)}, {@code false} otherwise.
     */
    boolean hasCompletions() {
        return !closed && ringHead != (int) INT_HANDLE.getVolatile(ktail, 0);
    }

    int count() {
        if (closed) {
            return 0;
        }
        return (int) INT_HANDLE.getVolatile(ktail, 0) - ringHead;
    }

    /**
     * Process the completion events in the {@link CompletionQueue} and return the number of processed
     * events.
     */
    int process(CompletionCallback callback) {
        if (closed) {
            return 0;
        }
        int tail = (int) INT_HANDLE.getVolatile(ktail, 0);
        try {
            int i = 0;
            while (ringHead != tail) {
                int cqePosition = cqeIdx(ringHead, ringMask);

                long udata = completionQueueArray.getLong(cqePosition + CQE_USER_DATA_FIELD);
                int res = completionQueueArray.getInt(cqePosition + CQE_RES_FIELD);
                int flags = completionQueueArray.getInt(cqePosition + CQE_FLAGS_FIELD);

                ringHead++;

                i++;
                if (!callback.handle(res, flags, udata)) {
                    // Stop processing. as the callback can not handle any more completions for now,
                    break;
                }
            }
            return i;
        } finally {
            // Ensure that the kernel only sees the new value of the head index after the CQEs have been read.
            INT_HANDLE.setRelease(khead, 0, ringHead);
        }
    }

    @Override
    public String toString() {
        StringJoiner sb = new StringJoiner(", ", "CompletionQueue [", "]");
        if (closed) {
            sb.add("closed");
        } else {
            int tail = (int) INT_HANDLE.getVolatile(ktail, 0);
            int head = ringHead;
            while (head != tail) {
                int cqePosition = cqeIdx(head++, ringMask);
                long udata = completionQueueArray.getLong(cqePosition + CQE_USER_DATA_FIELD);
                int res = completionQueueArray.getInt(cqePosition + CQE_RES_FIELD);
                int flags = completionQueueArray.getInt(cqePosition + CQE_FLAGS_FIELD);

                sb.add("(res=" + res).add(", flags=" + flags).add(", udata=" + udata).add(")");
            }
        }
        return sb.toString();
    }

    private static int cqeIdx(int ringHead, int ringMask) {
        return (ringHead & ringMask) * CQE_SIZE;
    }
}
