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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.util.internal.SocketUtils;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static io.netty.testsuite.transport.socket.SocketTestPermutation.BAD_HOST;
import static io.netty.testsuite.transport.socket.SocketTestPermutation.BAD_PORT;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SocketConnectionAttemptTest extends AbstractClientSocketTest {

    // See /etc/services
    private static final int UNASSIGNED_PORT = 4;

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
        ChannelFuture future = cb.connect(BAD_HOST, BAD_PORT);
        try {
            assertThat(future.await(3000), is(true));
        } finally {
            future.channel().close();
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
        ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                errorPromise.setFailure(new AssertionError("should have never been called"));
            }
        };

        cb.handler(handler);
        cb.option(ChannelOption.ALLOW_HALF_CLOSURE, halfClosure);
        ChannelFuture future = cb.connect(NetUtil.LOCALHOST, UNASSIGNED_PORT).awaitUninterruptibly();
        assertThat(future.cause(), is(instanceOf(ConnectException.class)));
        assertThat(errorPromise.cause(), is(nullValue()));
    }

    @Test
    public void testConnectCancellation(TestInfo testInfo) throws Throwable {
        // Check if the test can be executed or should be skipped because of no network/internet connection
        // See https://github.com/netty/netty/issues/1474
        boolean badHostTimedOut = true;
        Socket socket = new Socket();
        try {
            SocketUtils.connect(socket, SocketUtils.socketAddress(BAD_HOST, BAD_PORT), 10);
        } catch (ConnectException e) {
            badHostTimedOut = false;
            // is thrown for no route to host when using Socket connect
        } catch (Exception e) {
            // ignore
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }

        assumeTrue(badHostTimedOut, "The connection attempt to " + BAD_HOST + " does not time out.");

        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testConnectCancellation(bootstrap);
            }
        });
    }

    public void testConnectCancellation(Bootstrap cb) throws Throwable {
        cb.handler(new TestHandler()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000);
        ChannelFuture future = cb.connect(BAD_HOST, BAD_PORT);
        try {
            if (future.await(1000)) {
                if (future.isSuccess()) {
                    fail("A connection attempt to " + BAD_HOST + " must not succeed.");
                } else {
                    throw future.cause();
                }
            }

            if (future.cancel(true)) {
                assertThat(future.channel().closeFuture().await(500), is(true));
                assertThat(future.isCancelled(), is(true));
            } else {
                // Check if cancellation is supported or not.
                assertFalse(isConnectCancellationSupported(future.channel()), future.channel().getClass() +
                        " should support connect cancellation");
            }
        } finally {
            future.channel().close();
        }
    }

    @SuppressWarnings("deprecation")
    protected boolean isConnectCancellationSupported(Channel channel) {
        return !(channel instanceof OioSocketChannel);
    }

    private static class TestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            InternalLoggerFactory.getInstance(
                    SocketConnectionAttemptTest.class).warn("Unexpected exception:", cause);
        }
    }
}
