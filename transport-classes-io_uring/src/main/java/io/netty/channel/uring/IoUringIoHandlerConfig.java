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

import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration class for an {@link IoUringIoHandler},
 * managing the settings for a {@link RingBuffer} and its io_uring file descriptor.
 *
 * <h3>Option Map</h3>
 * These options are used exclusively during the initialization of the {@link IoUringIoHandler}
 * to configure the associated io_uring instance.
 *
 * <p>
 * The {@link IoUringIoHandlerConfig} class provides the following configurable options:
 * </p>
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 *   <caption>Available Configuration Options</caption>
 *   <thead>
 *     <tr>
 *       <th>Setter Method</th>
 *       <th>Description</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfig#setRingSize}</td>
 *       <td>Sets the size of the submission queue for the io_uring instance.</td>
 *     </tr>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfig#setMaxBoundedWorker}</td>
 *       <td>Defines the maximum number of bounded io_uring worker threads.</td>
 *     </tr>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfig#setMaxUnboundedWorker}</td>
 *       <td>Defines the maximum number of unbounded io_uring worker threads.</td>
 *     </tr>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfig#setBufferRingConfig}</td>
 *       <td>
 *         Adds a buffer ring configuration to the list of buffer ring configurations.
 *         It will be used to register the buffer ring for the io_uring instance.
 *       </td>
 *     </tr>
 *   </tbody>
 * </table>
 */

public final class IoUringIoHandlerConfig {
    private static final int DISABLE_SETUP_CQ_SIZE = -1;

    private int ringSize = Native.DEFAULT_RING_SIZE;
    private int cqSize = DISABLE_SETUP_CQ_SIZE;

    private int maxBoundedWorker;

    private int maxUnboundedWorker;

    private Set<IoUringBufferRingConfig> bufferRingConfigs;

    /**
     * Return the ring size of the io_uring instance.
     * @return the ring size of the io_uring instance.
     */
    public int getRingSize() {
        return ringSize;
    }

    /**
     * Return the size of the io_uring cqe.
     * @return the cq size of the io_uring.
     */
    public int getCqSize() {
        return cqSize;
    }

    /**
     * Return the maximum number of bounded iowq worker threads.
     * @return the maximum number of bounded iowq worker threads.
     */
    public int getMaxBoundedWorker() {
        return maxBoundedWorker;
    }

    /**
     * Return the maximum number of unbounded iowq worker threads.
     * @return the maximum number of unbounded iowq worker threads.
     */
    public int getMaxUnboundedWorker() {
        return maxUnboundedWorker;
    }

    /**
     * Set the ring size of the io_uring instance.
     * @param ringSize the ring size of the io_uring instance.
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfig setRingSize(int ringSize) {
        this.ringSize = ObjectUtil.checkPositive(ringSize, "ringSize");
        return this;
    }

    /**
     * Set the size of the io_uring cqe.
     * @param cqSize the size of the io_uring cqe.
     * @throws IllegalArgumentException if cqSize is less than ringSize, or not a power of 2
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfig setCqSize(int cqSize) {
        ObjectUtil.checkPositive(cqSize, "cqSize");
        this.cqSize = checkCqSize(cqSize);
        return this;
    }

    int checkCqSize(int cqSize) {
        if (cqSize < ringSize) {
            throw new IllegalArgumentException("cqSize must be greater than or equal to ringSize");
        }

        boolean isPowerOfTwo = Integer.bitCount(cqSize) == 1;
        if (!isPowerOfTwo) {
            throw new IllegalArgumentException("cqSize: " + cqSize + " (expected: power of 2)");
        }
        return cqSize;
    }

    /**
     * Set the maximum number of bounded iowq worker threads.
     * @param maxBoundedWorker the maximum number of bounded iowq worker threads,
     *                         or 0 for the Linux kernel default
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfig setMaxBoundedWorker(int maxBoundedWorker) {
        this.maxBoundedWorker = ObjectUtil.checkPositiveOrZero(maxBoundedWorker, "maxBoundedWorker");
        return this;
    }

    /**
     * Set the maximum number of unbounded iowq worker threads.
     * @param maxUnboundedWorker the maximum number of unbounded iowq worker threads,
     *                           of 0 for the Linux kernel default
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfig setMaxUnboundedWorker(int maxUnboundedWorker) {
        this.maxUnboundedWorker = ObjectUtil.checkPositiveOrZero(maxUnboundedWorker, "maxUnboundedWorker");
        return this;
    }

    /**
     * Add a buffer ring configuration to the list of buffer ring configurations.
     * Each {@link IoUringBufferRingConfig} must have a different {@link IoUringBufferRingConfig#bufferGroupId()}.
     *
     * @param ringConfig        the buffer ring configuration to append.
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfig setBufferRingConfig(IoUringBufferRingConfig... ringConfig) {
        Set<IoUringBufferRingConfig> configSet = new HashSet<>(ringConfig.length);
        for (IoUringBufferRingConfig bufferRingConfig : ringConfig) {
            if (!configSet.add(bufferRingConfig)) {
                throw new IllegalArgumentException("Duplicated buffer group id: " + bufferRingConfig.bufferGroupId());
            }
        }
        bufferRingConfigs = configSet;
        return this;
    }

    /**
     * Get the list of buffer ring configurations.
     * @return the copy of buffer ring configurations.
     */
    public List<IoUringBufferRingConfig> getBufferRingConfigs() {
        return new ArrayList<>(bufferRingConfigs);
    }

    boolean needRegisterIowqMaxWorker() {
        return maxBoundedWorker > 0 || maxUnboundedWorker > 0;
    }

    boolean needSetupCqeSize() {
        return cqSize != DISABLE_SETUP_CQ_SIZE;
    }

    Set<IoUringBufferRingConfig> getInternBufferRingConfigs() {
        return bufferRingConfigs;
    }
}
