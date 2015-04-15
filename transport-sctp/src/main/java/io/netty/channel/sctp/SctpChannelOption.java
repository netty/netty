/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import com.sun.nio.sctp.SctpStandardSocketOptions.InitMaxStreams;
import io.netty.channel.ChannelOption;

import java.net.SocketAddress;

import static io.netty.channel.ChannelOption.*;

/**
 * Option for configuring the SCTP transport
 */
public final class SctpChannelOption<T> extends ChannelOption<T> {

    @SuppressWarnings("rawtypes")
    private static final Class<SctpChannelOption> T = SctpChannelOption.class;

    public static final ChannelOption<Boolean> SCTP_DISABLE_FRAGMENTS = valueOf(T, "SCTP_DISABLE_FRAGMENTS");
    public static final ChannelOption<Boolean> SCTP_EXPLICIT_COMPLETE = valueOf(T, "SCTP_EXPLICIT_COMPLETE");
    public static final ChannelOption<Integer> SCTP_FRAGMENT_INTERLEAVE = valueOf(T, "SCTP_FRAGMENT_INTERLEAVE");
    public static final ChannelOption<InitMaxStreams> SCTP_INIT_MAXSTREAMS = valueOf(T, "SCTP_INIT_MAXSTREAMS");

    public static final ChannelOption<Boolean> SCTP_NODELAY = valueOf(T, "SCTP_NODELAY");
    public static final ChannelOption<SocketAddress> SCTP_PRIMARY_ADDR = valueOf(T, "SCTP_PRIMARY_ADDR");
    public static final ChannelOption<SocketAddress> SCTP_SET_PEER_PRIMARY_ADDR =
            valueOf(T, "SCTP_SET_PEER_PRIMARY_ADDR");

    @SuppressWarnings({ "unused", "deprecation" })
    private SctpChannelOption() {
        super(null);
    }
}
