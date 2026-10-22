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
package io.netty.channel.unix;

import io.netty.channel.ChannelOption;

import java.util.Set;

public class UnixChannelOption<T> extends ChannelOption<T> {
    public static final ChannelOption<Boolean> SO_REUSEPORT = valueOf(UnixChannelOption.class, "SO_REUSEPORT");
    public static final ChannelOption<DomainSocketReadMode> DOMAIN_SOCKET_READ_MODE =
            ChannelOption.valueOf(UnixChannelOption.class, "DOMAIN_SOCKET_READ_MODE");

    /**
     * The {@link ControlMessage}s that should be received together with each datagram. Netty takes care of enabling
     * the reception of the {@link ControlMessage}s and delivers each received datagram as
     * {@link ControlMessageDatagramPacket}, which contains the {@link ControlMessages} that were received. Use an
     * empty {@link Set} to disable the reception of control messages again, which is also the default.
     * <p>
     * It is recommended to set this option before the channel is registered (for example via the {@code Bootstrap}).
     * If it is changed afterwards, the change applies to all datagrams that are read after the change. Depending on
     * the transport reads might already be in progress (for example io_uring), in which case the newly requested
     * control messages might be missing for the datagrams that are received by these reads.
     * <p>
     * This is supported by the datagram channels of the native transports (epoll, io_uring and kqueue).
     */
    public static final ChannelOption<Set<ControlMessage<?>>> RECEIVE_CONTROL_MESSAGES =
            ChannelOption.valueOf(UnixChannelOption.class, "RECEIVE_CONTROL_MESSAGES");

    @SuppressWarnings({ "unused", "deprecation" })
    protected UnixChannelOption() {
        super(null);
    }

    UnixChannelOption(String name) {
        super(name);
    }
}
