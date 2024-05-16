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
package io.netty.channel.kqueue;

import io.netty.channel.IoOps;

/**
 * Implementation of {@link IoOps} for
 * that is used by {@link KQueueIoHandler} and so for kqueue based transports.
 */
public final class KQueueIoOps implements IoOps {
    private final short filter;
    private final short flags;
    private final int fflags;
    private final long data;

    /**
     * Creates a new {@link KQueueIoOps}.
     *
     * @param filter    the filter for this event.
     * @param flags     the general flags.
     * @param fflags    filter-specific flags.
     * @return          {@link KQueueIoOps}.
     */
    public static KQueueIoOps newOps(short filter, short flags, int fflags) {
        return new KQueueIoOps(filter, flags, fflags, 0);
    }

    private KQueueIoOps(short filter, short flags, int fflags, long data) {
        this.filter = filter;
        this.flags = flags;
        this.fflags = fflags;
        this.data = data;
    }

    /**
     * Returns the filter for this event.
     *
     * @return filter.
     */
    public short filter() {
        return filter;
    }

    /**
     * Returns the general flags.
     *
     * @return flags.
     */
    public short flags() {
        return flags;
    }

    /**
     * Returns filter-specific flags.
     *
     * @return fflags.
     */
    public int fflags() {
        return fflags;
    }

    /**
     * Returns filter-specific data.
     *
     * @return data.
     */
    public long data() {
        return data;
    }

    @Override
    public String toString() {
        return "KQueueIoOps{" +
                "filter=" + filter +
                ", flags=" + flags +
                ", fflags=" + fflags +
                ", data=" + data +
                '}';
    }
}
