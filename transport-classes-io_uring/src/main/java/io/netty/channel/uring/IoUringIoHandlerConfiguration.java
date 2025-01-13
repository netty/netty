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

/**
 * Configuration class for an {@link IoUringIoHandler},
 * managing the settings for a {@link RingBuffer} and its io_uring file descriptor.
 *
 * <h3>Option Map</h3>
 * These options are used exclusively during the initialization of the {@link IoUringIoHandler}
 * to configure the associated io_uring instance.
 *
 * <p>
 * The {@link IoUringIoHandlerConfiguration} class provides the following configurable options:
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
 *       <td>{@link IoUringIoHandlerConfiguration#setRingSize}</td>
 *       <td>Sets the size of the submission queue for the io_uring instance.</td>
 *     </tr>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfiguration#setMaxBoundedWorker}</td>
 *       <td>Defines the maximum number of bounded io_uring worker threads.</td>
 *     </tr>
 *     <tr>
 *       <td>{@link IoUringIoHandlerConfiguration#setMaxUnboundedWorker}</td>
 *       <td>Defines the maximum number of unbounded io_uring worker threads.</td>
 *     </tr>
 *   </tbody>
 * </table>
 */

public final class IoUringIoHandlerConfiguration {
    private int ringSize = Native.DEFAULT_RING_SIZE;

    private int maxBoundedWorker;

    private int maxUnboundedWorker;

    /**
     * return the ring size of the io_uring instance.
     * @return the ring size of the io_uring instance.
     */
    public int getRingSize() {
        return ringSize;
    }

    /**
     * return the maximum number of bounded iowq worker threads.
     * @return the maximum number of bounded iowq worker threads.
     */
    public int getMaxBoundedWorker() {
        return maxBoundedWorker;
    }

    /**
     * return the maximum number of unbounded iowq worker threads.
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
    public IoUringIoHandlerConfiguration setRingSize(int ringSize) {
        ObjectUtil.checkPositive(ringSize, "ringSize");
        this.ringSize = ringSize;
        return this;
    }

    /**
     * Set the maximum number of bounded iowq worker threads.
     * @param maxBoundedWorker the maximum number of bounded iowq worker threads.
     *                         if it is 0, We will not modify the number of threads
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfiguration setMaxBoundedWorker(int maxBoundedWorker) {
        ObjectUtil.checkPositiveOrZero(maxBoundedWorker, "maxBoundedWorker");
        this.maxBoundedWorker = maxBoundedWorker;
        return this;
    }

    /**
     * Set the maximum number of unbounded iowq worker threads.
     * @param maxUnboundedWorker the maximum number of unbounded iowq worker threads.
     *                           if it is 0, We will not modify the number of threads
     * @return reference to this, so the API can be used fluently
     */
    public IoUringIoHandlerConfiguration setMaxUnboundedWorker(int maxUnboundedWorker) {
        ObjectUtil.checkPositiveOrZero(maxUnboundedWorker, "maxUnboundedWorker");
        this.maxUnboundedWorker = maxUnboundedWorker;
        return this;
    }

    boolean needRegisterIOWQWorker() {
        return maxBoundedWorker > 0 || maxUnboundedWorker > 0;
    }
}
