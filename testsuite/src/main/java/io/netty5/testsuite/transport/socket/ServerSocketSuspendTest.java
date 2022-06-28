/*
 * Copyright 2012 The Netty Project
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

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.util.internal.SocketUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerSocketSuspendTest extends AbstractServerSocketTest {

    private static final int NUM_CHANNELS = 10;
    private static final long TIMEOUT = 3000000000L;

    @Test
    @Disabled("Need to investigate why it fails on osx")
    public void testSuspendAndResumeAccept(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSuspendAndResumeAccept);
    }

    public void testSuspendAndResumeAccept(ServerBootstrap sb) throws Throwable {
        AcceptedChannelCounter counter = new AcceptedChannelCounter(NUM_CHANNELS);

        sb.option(ChannelOption.SO_BACKLOG, 1);
        sb.option(ChannelOption.AUTO_READ, false);
        sb.childHandler(counter);

        Channel sc = sb.bind().get();

        List<Socket> sockets = new ArrayList<>();

        try {
            long startTime = System.nanoTime();
            for (int i = 0; i < NUM_CHANNELS; i ++) {
                Socket s = new Socket();
                SocketUtils.connect(s, sc.localAddress(), 10000);
                sockets.add(s);
            }

            sc.config().setAutoRead(true);

            counter.latch.await();

            long endTime = System.nanoTime();
            assertTrue(endTime - startTime > TIMEOUT);
        } finally {
            for (Socket s: sockets) {
                s.close();
            }
        }

        Thread.sleep(TIMEOUT / 1000000);

        try {
            long startTime = System.nanoTime();
            for (int i = 0; i < NUM_CHANNELS; i ++) {
                Socket s = new Socket();
                s.connect(sc.localAddress(), 10000);
                sockets.add(s);
            }
            long endTime = System.nanoTime();

            assertTrue(endTime - startTime < TIMEOUT);
        } finally {
            for (Socket s: sockets) {
                s.close();
            }
        }
    }

    private static final class AcceptedChannelCounter implements ChannelHandler {

        final CountDownLatch latch;

        AcceptedChannelCounter(int nChannels) {
            latch = new CountDownLatch(nChannels);
        }

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            latch.countDown();
        }
    }
}
