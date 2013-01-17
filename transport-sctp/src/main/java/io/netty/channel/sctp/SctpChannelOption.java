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

import io.netty.channel.ChannelOption;

import java.net.SocketAddress;
import java.util.List;

/**
 * Option for configuring the SCTP transport
 */
public class SctpChannelOption<T> extends ChannelOption<T> {
    public static final SctpChannelOption<Boolean> SCTP_DISABLE_FRAGMENTS =
            new SctpChannelOption<Boolean>("SCTP_DISABLE_FRAGMENTS");
    public static final SctpChannelOption<Boolean> SCTP_EXPLICIT_COMPLETE =
            new SctpChannelOption<Boolean>("SCTP_EXPLICIT_COMPLETE");
    public static final SctpChannelOption<Integer> SCTP_FRAGMENT_INTERLEAVE =
            new SctpChannelOption<Integer>("SCTP_FRAGMENT_INTERLEAVE");
    public static final SctpChannelOption<List<Integer>> SCTP_INIT_MAXSTREAMS =
            new SctpChannelOption<List<Integer>>("SCTP_INIT_MAXSTREAMS") {
                @Override
                public void validate(List<Integer> value) {
                    super.validate(value);
                    if (value.size() != 2) {
                        throw new IllegalArgumentException("value must be a List of 2 Integers: " + value);
                    }
                    if (value.get(0) == null) {
                        throw new NullPointerException("value[0]");
                    }
                    if (value.get(1) == null) {
                        throw new NullPointerException("value[1]");
                    }
                }
            };

    public static final SctpChannelOption<Boolean> SCTP_NODELAY =
            new SctpChannelOption<Boolean>("SCTP_NODELAY");
    public static final SctpChannelOption<SocketAddress> SCTP_PRIMARY_ADDR =
            new SctpChannelOption<SocketAddress>("SCTP_PRIMARY_ADDR");
    public static final SctpChannelOption<SocketAddress> SCTP_SET_PEER_PRIMARY_ADDR =
            new SctpChannelOption<SocketAddress>("SCTP_SET_PEER_PRIMARY_ADDR");

    private SctpChannelOption(String name) {
        super(name);
    }
}
