/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.address;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DynamicAddressConnectHandlerTest {
    private static final SocketAddress LOCAL = new SocketAddress() { };
    private static final SocketAddress LOCAL_NEW = new SocketAddress() { };
    private static final SocketAddress REMOTE = new SocketAddress() { };
    private static final SocketAddress REMOTE_NEW = new SocketAddress() { };
    @Test
    public void testReplaceAddresses() {

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                                SocketAddress localAddress, ChannelPromise promise) {
                try {
                    assertSame(REMOTE_NEW, remoteAddress);
                    assertSame(LOCAL_NEW, localAddress);
                    promise.setSuccess();
                } catch (Throwable cause) {
                    promise.setFailure(cause);
                }
            }
        }, new DynamicAddressConnectHandler() {
            @Override
            protected SocketAddress localAddress(SocketAddress remoteAddress, SocketAddress localAddress) {
                assertSame(REMOTE, remoteAddress);
                assertSame(LOCAL, localAddress);
                return LOCAL_NEW;
            }

            @Override
            protected SocketAddress remoteAddress(SocketAddress remoteAddress, SocketAddress localAddress) {
                assertSame(REMOTE, remoteAddress);
                assertSame(LOCAL, localAddress);
                return REMOTE_NEW;
            }
        });
        channel.connect(REMOTE, LOCAL).syncUninterruptibly();
        assertNull(channel.pipeline().get(DynamicAddressConnectHandler.class));
        assertFalse(channel.finish());
    }

    @Test
    public void testLocalAddressThrows() {
        testThrows0(true);
    }

    @Test
    public void testRemoteAddressThrows() {
        testThrows0(false);
    }

    private static void testThrows0(final boolean localThrows) {
        final IllegalStateException exception = new IllegalStateException();

        EmbeddedChannel channel = new EmbeddedChannel(new DynamicAddressConnectHandler() {
            @Override
            protected SocketAddress localAddress(
                    SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
                if (localThrows) {
                    throw exception;
                }
                return super.localAddress(remoteAddress, localAddress);
            }

            @Override
            protected SocketAddress remoteAddress(SocketAddress remoteAddress, SocketAddress localAddress)
                    throws Exception {
                if (!localThrows) {
                    throw exception;
                }
                return super.remoteAddress(remoteAddress, localAddress);
            }
        });
        assertSame(exception, channel.connect(REMOTE, LOCAL).cause());
        assertNotNull(channel.pipeline().get(DynamicAddressConnectHandler.class));
        assertFalse(channel.finish());
    }
}
