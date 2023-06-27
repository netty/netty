/*
 * Copyright 2021 The Netty Project
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

import io.netty.channel.Channel;

/**
 * A {@link UnixChannel} that supports communication via
 * <a href="https://en.wikipedia.org/wiki/Unix_domain_socket">UNIX domain datagram sockets</a>.
 */
public interface DomainDatagramChannel extends UnixChannel, Channel {

    @Override
    DomainDatagramChannelConfig config();

    /**
     * Return {@code true} if the {@link DomainDatagramChannel} is connected to the remote peer.
     */
    boolean isConnected();

    @Override
    DomainSocketAddress localAddress();

    @Override
    DomainSocketAddress remoteAddress();
}
