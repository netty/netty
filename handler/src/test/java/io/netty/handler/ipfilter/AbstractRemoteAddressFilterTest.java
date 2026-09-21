/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ipfilter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractRemoteAddressFilterTest {

    @Test
    public void channelIsClosedWhenAcceptThrows() {
        final RuntimeException boom = new RuntimeException("boom");
        AbstractRemoteAddressFilter<InetSocketAddress> filter = new AbstractRemoteAddressFilter<InetSocketAddress>() {
            @Override
            protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
                throw boom;
            }
        };

        final EmbeddedChannel channel = new EmbeddedChannel(filter) {
            @Override
            protected SocketAddress remoteAddress0() {
                return isActive() ? SocketUtils.socketAddress("10.0.0.1", 1234) : null;
            }
        };

        // The channel must be closed rather than left open and unfiltered when accept() throws.
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                channel.checkException();
            }
        });
    }
}
