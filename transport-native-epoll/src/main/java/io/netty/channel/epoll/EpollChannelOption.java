/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.channel.ChannelOption;
import io.netty.channel.unix.DomainSocketReadMode;

import java.net.InetAddress;
import java.util.Map;

public final class EpollChannelOption<T> extends ChannelOption<T> {

    public static final ChannelOption<Boolean> TCP_CORK = valueOf("TCP_CORK");
    public static final ChannelOption<Boolean> SO_REUSEPORT = valueOf("SO_REUSEPORT");
    public static final ChannelOption<Long> TCP_NOTSENT_LOWAT = valueOf("TCP_NOTSENT_LOWAT");
    public static final ChannelOption<Integer> TCP_KEEPIDLE = valueOf("TCP_KEEPIDLE");
    public static final ChannelOption<Integer> TCP_KEEPINTVL = valueOf("TCP_KEEPINTVL");
    public static final ChannelOption<Integer> TCP_KEEPCNT = valueOf("TCP_KEEPCNT");
    public static final ChannelOption<Integer> TCP_USER_TIMEOUT = valueOf("TCP_USER_TIMEOUT");
    public static final ChannelOption<Boolean> IP_FREEBIND = valueOf("IP_FREEBIND");
    public static final ChannelOption<Integer> TCP_FASTOPEN = valueOf("TCP_FASTOPEN");
    public static final ChannelOption<Integer> TCP_DEFER_ACCEPT = valueOf("TCP_DEFER_ACCEPT");
    public static final ChannelOption<Boolean> TCP_QUICKACK = valueOf("TCP_QUICKACK");

    public static final ChannelOption<DomainSocketReadMode> DOMAIN_SOCKET_READ_MODE =
            valueOf("DOMAIN_SOCKET_READ_MODE");

    public static final ChannelOption<EpollMode> EPOLL_MODE =
            valueOf("EPOLL_MODE");

    public static final ChannelOption<Map<InetAddress, byte[]>> TCP_MD5SIG = valueOf("TCP_MD5SIG");

    @SuppressWarnings({ "unused", "deprecation" })
    private EpollChannelOption(String name) {
        super(name);
    }
}
