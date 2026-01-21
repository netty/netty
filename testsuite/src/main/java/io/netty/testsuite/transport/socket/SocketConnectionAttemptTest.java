/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.util.concurrent.Future;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import static io.netty.testsuite.transport.socket.SocketTestPermutation.BAD_HOST;
import static io.netty.testsuite.transport.socket.SocketTestPermutation.BAD_PORT;
import static io.netty.testsuite.transport.socket.SocketTestPermutation.UNASSIGNED_PORT;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketConnectionAttemptTest extends AbstractClientSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testConnectTimeout(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testConnectTimeout(bootstrap);
            }
        });
    }

    public void testConnectTimeout(Bootstrap cb) throws Throwable {
        cb.handler(new TestHandler()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
        Future<Channel> future = cb.connect(BAD_HOST, BAD_PORT);
        try {
            assertTrue(future.await(3000));
        } finally {
            if (future.isSuccess()) {
                future.getNow().close();
            }
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testConnectRefused(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testConnectRefused(bootstrap);
            }
        });
    }

    public void testConnectRefused(Bootstrap cb) throws Throwable {
        testConnectRefused0(cb, false);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testConnectRefusedHalfClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testConnectRefusedHalfClosure(bootstrap);
            }
        });
    }

    public void testConnectRefusedHalfClosure(Bootstrap cb) throws Throwable {
        testConnectRefused0(cb, true);
    }

    private static void testConnectRefused0(Bootstrap cb, boolean halfClosure) throws Throwable {
        final Promise<Error> errorPromise = GlobalEventExecutor.INSTANCE.newPromise();
        ChannelHandler handler = new ChannelInboundHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                errorPromise.setFailure(new AssertionError("should have never been called"));
            }
        };

        cb.handler(handler);
        cb.option(ChannelOption.ALLOW_HALF_CLOSURE, halfClosure);
        Future<Channel> future = cb.connect(NetUtil.LOCALHOST, UNASSIGNED_PORT).awaitUninterruptibly();
        assertInstanceOf(ConnectException.class, future.cause());
        assertNull(errorPromise.cause());
    }

    private static class TestHandler implements ChannelInboundHandler {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            InternalLoggerFactory.getInstance(
                    SocketConnectionAttemptTest.class).warn("Unexpected exception:", cause);
        }
    }
}
