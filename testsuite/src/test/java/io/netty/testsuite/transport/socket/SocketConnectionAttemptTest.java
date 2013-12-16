/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

public class SocketConnectionAttemptTest extends AbstractClientSocketTest {

    private static final String BAD_HOST = SystemPropertyUtil.get("io.netty.testsuite.badHost", "255.255.255.0");

    static {
        InternalLoggerFactory.getInstance(SocketConnectionAttemptTest.class).debug(
                "-Dio.netty.testsuite.badHost: {}", BAD_HOST);
    }

    @Test(timeout = 30000)
    public void testConnectTimeout() throws Throwable {
        run();
    }

    public void testConnectTimeout(Bootstrap cb) throws Throwable {
        cb.handler(new ChannelHandlerAdapter()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
        ChannelFuture future = cb.connect(BAD_HOST, 8080);
        try {
            assertThat(future.await(3000), is(true));
        } finally {
            future.channel().close();
        }
    }

    @Test
    public void testConnectCancellation() throws Throwable {
        // Check if the test can be executed or should be skipped because of no network/internet connection
        // See https://github.com/netty/netty/issues/1474
        boolean badHostTimedOut = true;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(BAD_HOST, 8080), 10);
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

        assumeThat("The connection attempt to " + BAD_HOST + " does not time out.", badHostTimedOut, is(true));

        run();
    }

    public void testConnectCancellation(Bootstrap cb) throws Throwable {
        cb.handler(new ChannelHandlerAdapter()).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4000);
        ChannelFuture future = cb.connect(BAD_HOST, 8080);
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
                // Cancellation not supported by the transport.
            }
        } finally {
            future.channel().close();
        }
    }
}
