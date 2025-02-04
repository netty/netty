/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.internal.LongCounter;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.Map;

/**
 * Metric for {@link IoUring}.
 */
public final class IoUringMetric {

    private static final boolean ENABLE_METRIC = SystemPropertyUtil.getBoolean("io.netty.iouring.enableMetric", true);

    private static final LongCounter SQE_HANDLE_COUNTER = PlatformDependent.newLongCounter();

    private static final LongCounter CQE_HANDLE_COUNT = PlatformDependent.newLongCounter();

    private static final LongCounter PROVIDER_BUFFER_READ_FAIL_COUNT = PlatformDependent.newLongCounter();

    /**
     * Use map instead of directly using CompletionQueue to prevent illegal access after ioUring is closed
     */
    private static final Map<CompletionQueue, Integer> OVERFLOW_RECORD = PlatformDependent.newConcurrentHashMap();

    private static final LongCounter OVERFLOW_FROM_CLOSED_CQE = PlatformDependent.newLongCounter();

    private IoUringMetric() {
        // utility
    }

    static void increaseSqeCounter(int count) {
        if (ENABLE_METRIC) {
            SQE_HANDLE_COUNTER.add(count);
        }
    }

    static void increaseCqeCounter(int count) {
        if (ENABLE_METRIC) {
            CQE_HANDLE_COUNT.add(count);
        }
    }

    static void increaseProviderBufferReadFailCounter() {
        if (ENABLE_METRIC) {
            PROVIDER_BUFFER_READ_FAIL_COUNT.increment();
        }
    }

    static void recordOverflowCounter(CompletionQueue queue, int kflowValue) {
        if (ENABLE_METRIC) {
            OVERFLOW_RECORD.put(queue, kflowValue);
        }
    }

    static void recordIOUringClose(RingBuffer ringBuffer) {
        CompletionQueue completionQueue = ringBuffer.ioUringCompletionQueue();
        OVERFLOW_FROM_CLOSED_CQE.add(completionQueue.getKoverflow());
    }

    /**
     * The number of submitted SQEs submitted
     *
     * @return the number of SQEs submitted
     */
    public static long sqeCounter() {
        return ENABLE_METRIC ? SQE_HANDLE_COUNTER.value() : 0;
    }

    /**
     * The number of handle processed CQEs
     *
     * @return The number of handle processed CQEs
     */
    public static long cqeCounter() {
        return ENABLE_METRIC ? CQE_HANDLE_COUNT.value() : 0;
    }

    /**
     *  if the kernel supports IORING_FEAT_NODROP the ring enters a CQ ring overflow state.
     *  Otherwise it drops the CQEs and increments cq.koverflow in struct io_uring with the number of CQEs dropped
     *
     * @return The number of overflowed CQEs
     */
    public static long overflowCounter() {
        if (!ENABLE_METRIC || IoUring.isIoUringNoDropSupported()) {
            return 0;
        }
        long count = 0;

        for (Integer value : OVERFLOW_RECORD.values()) {
            count += value;
        }

        return count + OVERFLOW_FROM_CLOSED_CQE.value();
    }

    /**
     * The number of buffer ring exhaustion occurrences
     *
     * @return the number of buffer ring exhaustion occurrences
     */
    public static long providerBufferReadFailCounter() {
        return ENABLE_METRIC && IoUring.isRegisterBufferRingSupported() ? PROVIDER_BUFFER_READ_FAIL_COUNT.value() : 0;
    }
}
