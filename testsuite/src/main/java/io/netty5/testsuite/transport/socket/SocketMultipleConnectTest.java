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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.nio.channels.AlreadyConnectedException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketMultipleConnectTest extends AbstractSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testMultipleConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testMultipleConnect);
    }

    public void testMultipleConnect(ServerBootstrap sb, Bootstrap cb) throws Exception {
        Channel sc = null;
        Channel cc = null;
        try {
            sb.childHandler(new ChannelHandler() { });
            sc = sb.bind(NetUtil.LOCALHOST, 0).get();

            cb.handler(new ChannelHandler() { });
            cc = cb.register().get();
            cc.connect(sc.localAddress()).syncUninterruptibly();
            Future<Void> connectFuture2 = cc.connect(sc.localAddress()).await();
            assertTrue(connectFuture2.cause() instanceof AlreadyConnectedException);
        } finally {
            if (cc != null) {
                cc.close();
            }
            if (sc != null) {
                sc.close();
            }
        }
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return new ArrayList<>(SocketTestPermutation.INSTANCE.socketWithFastOpen());
    }
}
