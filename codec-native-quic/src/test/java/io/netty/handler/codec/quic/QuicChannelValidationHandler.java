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
package io.netty.handler.codec.quic;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class QuicChannelValidationHandler extends ChannelInboundHandlerAdapter {
    private volatile boolean wasActive;

    private volatile QuicConnectionAddress localAddress;
    private volatile QuicConnectionAddress remoteAddress;
    private volatile Throwable cause;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        this.cause = cause;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        localAddress = (QuicConnectionAddress) ctx.channel().localAddress();
        remoteAddress = (QuicConnectionAddress) ctx.channel().remoteAddress();
        wasActive = true;
        ctx.fireChannelActive();
    }

    QuicConnectionAddress localAddress() {
        return localAddress;
    }

    QuicConnectionAddress remoteAddress() {
        return remoteAddress;
    }

    void assertState() throws Throwable {
        if (cause != null) {
            throw cause;
        }
        if (wasActive) {
            // Validate that the addresses could be retrieved
            assertNotNull(localAddress);
            assertNotNull(remoteAddress);
        }
    }
}
