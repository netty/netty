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
package io.netty.channel;


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;

import static org.junit.Assert.*;

class BaseChannelTest {

    private final LoggingHandler loggingHandler;

    BaseChannelTest() {
        loggingHandler = new LoggingHandler();
    }

    ServerBootstrap getLocalServerBootstrap() {
        EventLoopGroup serverGroup = new DefaultEventLoopGroup();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverGroup);
        sb.channel(LocalServerChannel.class);
        sb.childHandler(new ChannelInitializer<LocalChannel>() {
            @Override
            public void initChannel(LocalChannel ch) throws Exception {
            }
        });

        return sb;
    }

    Bootstrap getLocalClientBootstrap() {
        EventLoopGroup clientGroup = new DefaultEventLoopGroup();
        Bootstrap cb = new Bootstrap();
        cb.channel(LocalChannel.class);
        cb.group(clientGroup);

        cb.handler(loggingHandler);

        return cb;
    }

    static ByteBuf createTestBuf(int len) {
        ByteBuf buf = Unpooled.buffer(len, len);
        buf.setIndex(0, len);
        return buf;
    }

    void assertLog(String firstExpected, String... otherExpected) {
        String actual = loggingHandler.getLog();
        if (firstExpected.equals(actual)) {
            return;
        }
        for (String e: otherExpected) {
            if (e.equals(actual)) {
                return;
            }
        }

        // Let the comparison fail with the first expectation.
        assertEquals(firstExpected, actual);
    }

    void clearLog() {
        loggingHandler.clear();
    }

    void setInterest(LoggingHandler.Event... events) {
        loggingHandler.setInterest(events);
    }

}
