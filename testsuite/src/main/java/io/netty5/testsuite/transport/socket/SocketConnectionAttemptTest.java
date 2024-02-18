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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ConnectTimeoutException;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.GlobalEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SocketUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.Socket;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static io.netty5.testsuite.transport.socket.SocketTestPermutation.BAD_HOST;
import static io.netty5.testsuite.transport.socket.SocketTestPermutation.BAD_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SocketConnectionAttemptTest extends AbstractClientSocketTest {

    // See /etc/services
    private static final int UNASSIGNED_PORT = 4;

    @Test
    @Timeout(30)
    public void testConnectTimeout(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConnectTimeout);
    }

    public void testConnectTimeout(Bootstrap cb) throws Throwable {
        cb.handler(new TestHandler()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 200);
        Future<Channel> future = cb.connect(BAD_HOST, BAD_PORT);
        CompletionException e = assertThrows(CompletionException.class, () -> future.asStage().sync());
        assertThat(e).hasCauseInstanceOf(ConnectTimeoutException.class);
    }

    @Test
    @Timeout(30)
    public void testConnectRefused(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConnectRefused);
    }

    public void testConnectRefused(Bootstrap cb) throws Throwable {
        testConnectRefused0(cb, false);
    }

    @Test
    @Timeout(30)
    public void testConnectRefusedHalfClosure(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testConnectRefusedHalfClosure);
    }

    public void testConnectRefusedHalfClosure(Bootstrap cb) throws Throwable {
        testConnectRefused0(cb, true);
    }

    private static void testConnectRefused0(Bootstrap cb, boolean halfClosure) throws Throwable {
        final Promise<Error> errorPromise = GlobalEventExecutor.INSTANCE.newPromise();
        ChannelHandler handler = new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                errorPromise.setFailure(new AssertionError("should have never been called"));
            }
        };

        cb.handler(handler);
        cb.option(ChannelOption.ALLOW_HALF_CLOSURE, halfClosure);
        Throwable cause = cb.connect(NetUtil.LOCALHOST, UNASSIGNED_PORT).asStage().getCause();
        assertThat(cause).isInstanceOf(ConnectException.class);
        assertFalse(errorPromise.isFailed());
    }

    @Test
    public void testConnectCancellation(TestInfo testInfo) throws Throwable {
        // Check if the test can be executed or should be skipped because of no network/internet connection
        // See https://github.com/netty/netty/issues/1474
        boolean badHostTimedOut = true;
        try (Socket socket = new Socket()) {
            SocketUtils.connect(socket, SocketUtils.socketAddress(BAD_HOST, BAD_PORT), 10);
        } catch (ConnectException e) {
            badHostTimedOut = false;
            // is thrown for no route to host when using Socket connect
        } catch (Exception e) {
            // ignore
        }
        // ignore

        assumeTrue(badHostTimedOut, "The connection attempt to " + BAD_HOST + " does not time out.");

        run(testInfo, this::testConnectCancellation);
    }

    public void testConnectCancellation(Bootstrap cb) throws Throwable {
        cb.handler(new TestHandler()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000);
        Future<Channel> future = cb.connect(BAD_HOST, BAD_PORT);
        if (future.asStage().await(1000, TimeUnit.MILLISECONDS)) {
            if (future.isSuccess()) {
                fail("A connection attempt to " + BAD_HOST + " must not succeed.");
            } else {
                throw future.cause();
            }
        }

        assertTrue(future.cancel());
        assertThat(future.isCancelled()).isTrue();
    }

    private static class TestHandler implements ChannelHandler {
        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LoggerFactory.getLogger(SocketConnectionAttemptTest.class).warn("Unexpected exception:", cause);
        }
    }
}
