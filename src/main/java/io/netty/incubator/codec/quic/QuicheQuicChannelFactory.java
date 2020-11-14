/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.util.internal.ObjectUtil;

/**
 * A {@link ChannelFactory} that is used for clients.
 */
final class QuicheQuicChannelFactory implements ChannelFactory<QuicheQuicChannel> {

    private final Channel parent;

    QuicheQuicChannelFactory(Channel parent) {
        this.parent = ObjectUtil.checkNotNull(parent, "parent");
    }

    @Override
    public QuicheQuicChannel newChannel() {
        return QuicheQuicChannel.forClient(parent);
    }
}
