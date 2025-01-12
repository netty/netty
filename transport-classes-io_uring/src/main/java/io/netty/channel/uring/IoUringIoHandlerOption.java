/*
 * Copyright 2014 The Netty Project
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

public class IoUringIoHandlerOption {
    private int ringSize = Native.DEFAULT_RING_SIZE;

    private int maxBoundedWorker = -1;

    private int maxUnboundedWorker = -1;

    public int getRingSize() {
        return ringSize;
    }

    public int getMaxBoundedWorker() {
        return maxBoundedWorker;
    }

    public int getMaxUnboundedWorker() {
        return maxUnboundedWorker;
    }

    public IoUringIoHandlerOption setRingSize(int ringSize) {
        this.ringSize = ringSize;
        return this;
    }

    public IoUringIoHandlerOption setMaxBoundedWorker(int maxBoundedWorker) {
        this.maxBoundedWorker = maxBoundedWorker;
        return this;
    }

    public IoUringIoHandlerOption setMaxUnboundedWorker(int maxUnboundedWorker) {
        this.maxUnboundedWorker = maxUnboundedWorker;
        return this;
    }

    boolean needRegisterIOWQWorker() {
        return maxBoundedWorker > 0 || maxUnboundedWorker > 0;
    }
}
