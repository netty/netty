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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class DatagramUnicastInetTest extends DatagramUnicastTest {

    @Test
    public void testBindWithPortOnly(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<Bootstrap, Bootstrap>() {
            @Override
            public void run(Bootstrap bootstrap, Bootstrap bootstrap2) throws Throwable {
                testBindWithPortOnly(bootstrap2);
            }
        });
    }

    private static void testBindWithPortOnly(Bootstrap cb) throws Throwable {
        Channel channel = null;
        try {
            cb.handler(new ChannelHandlerAdapter() { });
            channel = cb.bind(0).sync().channel();
        } finally {
            closeChannel(channel);
        }
    }
}
