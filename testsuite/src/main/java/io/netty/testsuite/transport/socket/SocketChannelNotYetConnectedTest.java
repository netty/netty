/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import java.net.SocketException;
import java.nio.channels.NotYetConnectedException;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.fail;

public class SocketChannelNotYetConnectedTest extends AbstractClientSocketTest {
    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownNotYetConnected(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap) throws Throwable {
                testShutdownNotYetConnected(bootstrap);
            }
        });
    }

    public void testShutdownNotYetConnected(Bootstrap cb) throws Throwable {
        SocketChannel ch = (SocketChannel) cb.handler(new ChannelInboundHandlerAdapter())
                .bind(newSocketAddress()).syncUninterruptibly().channel();
        try {
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }

            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
        } finally {
            ch.close().syncUninterruptibly();
        }
    }

    private static void checkThrowable(Throwable cause) throws Throwable {
        // Depending on OIO / NIO both are ok
        if (!(cause instanceof NotYetConnectedException) && !(cause instanceof SocketException)) {
            throw cause;
        }
    }
}
