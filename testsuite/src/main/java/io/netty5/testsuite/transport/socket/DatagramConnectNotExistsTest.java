/*
 * Copyright 2017 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.PortUnreachableException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class DatagramConnectNotExistsTest extends AbstractClientSocketTest {

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagramSocket();
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testConnectNotExistsByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConnectNotExistsByteBuf);
    }

    public void testConnectNotExistsByteBuf(Bootstrap cb) throws Throwable {
        // Currently, not works on windows
        // See https://github.com/netty/netty/issues/11285
        assumeFalse(PlatformDependent.isWindows());
        final Promise<Throwable> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        cb.handler(new ChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                promise.trySuccess(cause);
            }
        });
        Future<Channel> future = cb.connect(NetUtil.LOCALHOST, SocketTestPermutation.BAD_PORT);
        Channel datagramChannel = null;
        try {
            datagramChannel = future.get();
            assertTrue(datagramChannel.isActive());
            datagramChannel.writeAndFlush(
                    Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)).syncUninterruptibly();
            assertThat(promise.asFuture().syncUninterruptibly().getNow()).isInstanceOf(PortUnreachableException.class);
        } finally {
            if (datagramChannel != null) {
                datagramChannel.close();
            }
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testConnectNotExists(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConnectNotExists);
    }

    public void testConnectNotExists(Bootstrap cb) throws Throwable {
        // Currently, not works on windows
        // See https://github.com/netty/netty/issues/11285
        assumeFalse(PlatformDependent.isWindows());
        final Promise<Throwable> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        cb.handler(new ChannelHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                promise.trySuccess(cause);
            }
        });
        Future<Channel> future = cb.connect(NetUtil.LOCALHOST, SocketTestPermutation.BAD_PORT);
        Channel datagramChannel = null;
        try {
            datagramChannel = future.get();
            assertTrue(datagramChannel.isActive());
            BufferAllocator allocator = datagramChannel.bufferAllocator();
            datagramChannel.writeAndFlush(
                    allocator.copyOf("test".getBytes(CharsetUtil.US_ASCII))).syncUninterruptibly();
            assertTrue(promise.asFuture().syncUninterruptibly().getNow() instanceof PortUnreachableException);
        } finally {
            if (datagramChannel != null) {
                datagramChannel.close();
            }
        }
    }
}
