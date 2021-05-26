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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.net.PortUnreachableException;
import java.util.List;

public class DatagramConnectNotExistsTest extends AbstractClientSocketTest {

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagramSocket();
    }

    @Test(timeout = 10000)
    public void testConnectNotExists() throws Throwable {
        run();
    }

    public void testConnectNotExists(Bootstrap cb) throws Throwable {
        // Currently not works on windows
        // See https://github.com/netty/netty/issues/11285
        Assume.assumeFalse(PlatformDependent.isWindows());
        final Promise<Throwable> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        cb.handler(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                promise.trySuccess(cause);
            }
        });
        ChannelFuture future = cb.connect(NetUtil.LOCALHOST, SocketTestPermutation.BAD_PORT);
        try {
            Channel datagramChannel = future.syncUninterruptibly().channel();
            Assert.assertTrue(datagramChannel.isActive());
            datagramChannel.writeAndFlush(
                    Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)).syncUninterruptibly();
            if (!(datagramChannel instanceof OioDatagramChannel)) {
                Assert.assertTrue(promise.syncUninterruptibly().getNow() instanceof PortUnreachableException);
            }
        } finally {
            future.channel().close();
        }
    }
}
