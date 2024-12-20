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

import io.netty.channel.ChannelOption;
import io.netty.channel.unix.UnixChannelOption;

public final class IoUringChannelOption<T> extends UnixChannelOption<T> {

    private IoUringChannelOption() { }

    public static final ChannelOption<Boolean> TCP_CORK = valueOf(IoUringChannelOption.class, "TCP_CORK");
    public static final ChannelOption<Long> TCP_NOTSENT_LOWAT =
            valueOf(IoUringChannelOption.class, "TCP_NOTSENT_LOWAT");
    public static final ChannelOption<Integer> TCP_KEEPIDLE = valueOf(IoUringChannelOption.class, "TCP_KEEPIDLE");
    public static final ChannelOption<Integer> TCP_KEEPINTVL = valueOf(IoUringChannelOption.class, "TCP_KEEPINTVL");
    public static final ChannelOption<Integer> TCP_KEEPCNT = valueOf(IoUringChannelOption.class, "TCP_KEEPCNT");
    public static final ChannelOption<Integer> TCP_USER_TIMEOUT =
            valueOf(IoUringChannelOption.class, "TCP_USER_TIMEOUT");
    public static final ChannelOption<Boolean> IP_FREEBIND = valueOf("IP_FREEBIND");
    public static final ChannelOption<Boolean> IP_TRANSPARENT = valueOf("IP_TRANSPARENT");
    /**
     * @deprecated Use {@link ChannelOption#TCP_FASTOPEN} instead.
     */
    public static final ChannelOption<Integer> TCP_FASTOPEN = ChannelOption.TCP_FASTOPEN;

    public static final ChannelOption<Integer> TCP_DEFER_ACCEPT =
            ChannelOption.valueOf(IoUringChannelOption.class, "TCP_DEFER_ACCEPT");
    public static final ChannelOption<Boolean> TCP_QUICKACK = valueOf(IoUringChannelOption.class, "TCP_QUICKACK");

    public static final ChannelOption<Integer> MAX_DATAGRAM_PAYLOAD_SIZE = valueOf("MAX_DATAGRAM_PAYLOAD_SIZE");

    /**
     * When set to {@code true} a POLLIN will be scheduled to get notified once there is something to read.
     * Once the notification is received the actual reading is scheduled. This means one extra operation has to be
     * scheduled for each read-loop but also means that we will not need to reserve any extra memory until there is
     * something to read for real.
     * When set to {@code false} the read is submitted directly, which has the pro that there are less operations that
     * need to be scheduled per read-loop. That said it also means that the memory for the read needs to be reserved
     * upfront even if we are not sure yet when exactly the read will happen.
     */
    public static final ChannelOption<Boolean> POLLIN_FIRST = valueOf("POLLIN_FIRST");
}
