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
package io.netty.handler.codec.compression;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

/**
 * This class centralizes backpressure accounting and, more importantly, configuration for decompression
 * {@link io.netty.channel.ChannelHandler}s.
 */
public final class BackpressureGauge {
    private final int messagesPerRead;
    private int downstreamMessageBudget;

    private final long bytesPerRead;
    private long downstreamBytesBudget;

    BackpressureGauge(Builder builder) {
        this.messagesPerRead = builder.messagesPerRead;
        this.bytesPerRead = builder.bytesPerRead;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Count a framing message that does not have a clearly defined byte count.
     */
    public void countNonByteMessage() {
        countMessage(1);
    }

    /**
     * Count a message.
     *
     * @param bytes The number of bytes
     */
    public void countMessage(long bytes) {
        downstreamMessageBudget--;
        downstreamBytesBudget -= bytes;
    }

    /**
     * Relieve backpressure on this gauge, resetting the limits back to their configured maximum.
     */
    public void relieveBackpressure() {
        downstreamMessageBudget = messagesPerRead;
        downstreamBytesBudget = bytesPerRead;
    }

    /**
     * Increase backpressure so that future {@link #backpressureLimitExceeded()} calls will return {@code true} until
     * backpressure is relieved again.
     */
    public void increaseBackpressure() {
        downstreamMessageBudget = 0;
    }

    /**
     * Check whether the backpressure limit has been exceeded and we should stop sending messages until
     * {@link #relieveBackpressure()} is called.
     *
     * @return {@code true} if we should stop sending messages
     */
    public boolean backpressureLimitExceeded() {
        return downstreamMessageBudget <= 0 || downstreamBytesBudget <= 0;
    }

    public static final class Builder {
        int messagesPerRead = 64;
        long bytesPerRead = 1024 * 1024;

        Builder() {
        }

        /**
         * Configure the maximum message target per {@link ChannelHandlerContext#read() read operation}. Note that this
         * is a rough guideline, and unlike {@code FlowControlHandler}, this limit can sometimes be exceeded.
         * <p>
         * The default value is 64. The purpose of this limit is to prevent uncontrolled decompression ("zip bombs").
         *
         * @param messagesPerRead The maximum number of messages per read operation
         * @return This builder
         */
        public Builder messagesPerRead(int messagesPerRead) {
            this.messagesPerRead = ObjectUtil.checkPositive(messagesPerRead, "messagesPerRead");
            return this;
        }

        /**
         * Configure the maximum number of bytes per {@link ChannelHandlerContext#read() read operation}. Note that
         * this is a rough guideline, and this limit can sometimes be exceeded.
         * <p>
         * The default value is 1MiB. The purpose of this limit is to prevent uncontrolled decompression ("zip bombs").
         *
         * @param bytesPerRead The maximum number of bytes per read operation
         * @return This builder
         */
        public Builder bytesPerRead(long bytesPerRead) {
            this.bytesPerRead = ObjectUtil.checkPositive(bytesPerRead, "bytesPerRead");
            return this;
        }

        public BackpressureGauge build() {
            return new BackpressureGauge(this);
        }
    }
}
