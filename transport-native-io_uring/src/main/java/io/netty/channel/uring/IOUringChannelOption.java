/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.ChannelOption;
import io.netty.channel.unix.UnixChannelOption;

import java.net.InetAddress;
import java.util.Map;

public class IOUringChannelOption<T> extends UnixChannelOption<T> {

    public static final ChannelOption<Boolean> TCP_CORK = valueOf(IOUringChannelOption.class, "TCP_CORK");
    public static final ChannelOption<Long> TCP_NOTSENT_LOWAT =
            valueOf(IOUringChannelOption.class, "TCP_NOTSENT_LOWAT");
    public static final ChannelOption<Integer> TCP_KEEPIDLE = valueOf(IOUringChannelOption.class, "TCP_KEEPIDLE");
    public static final ChannelOption<Integer> TCP_KEEPINTVL = valueOf(IOUringChannelOption.class, "TCP_KEEPINTVL");
    public static final ChannelOption<Integer> TCP_KEEPCNT = valueOf(IOUringChannelOption.class, "TCP_KEEPCNT");
    public static final ChannelOption<Integer> TCP_USER_TIMEOUT =
            valueOf(IOUringChannelOption.class, "TCP_USER_TIMEOUT");
    public static final ChannelOption<Boolean> IP_FREEBIND = valueOf("IP_FREEBIND");
    public static final ChannelOption<Boolean> IP_TRANSPARENT = valueOf("IP_TRANSPARENT");
    public static final ChannelOption<Boolean> IP_RECVORIGDSTADDR = valueOf("IP_RECVORIGDSTADDR");
    public static final ChannelOption<Integer> TCP_FASTOPEN = valueOf(IOUringChannelOption.class, "TCP_FASTOPEN");
    public static final ChannelOption<Boolean> TCP_FASTOPEN_CONNECT =
            valueOf(IOUringChannelOption.class, "TCP_FASTOPEN_CONNECT");
    public static final ChannelOption<Integer> TCP_DEFER_ACCEPT =
            ChannelOption.valueOf(IOUringChannelOption.class, "TCP_DEFER_ACCEPT");
    public static final ChannelOption<Boolean> TCP_QUICKACK = valueOf(IOUringChannelOption.class, "TCP_QUICKACK");
    public static final ChannelOption<Map<InetAddress, byte[]>> TCP_MD5SIG = valueOf("TCP_MD5SIG");
}
