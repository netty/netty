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
package io.netty5.channel;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseChannelTest {

    private final LoggingHandler loggingHandler;

    BaseChannelTest() {
        loggingHandler = new LoggingHandler();
    }

    ServerBootstrap getLocalServerBootstrap() {
        EventLoopGroup serverGroup = new MultithreadEventLoopGroup(LocalHandler.newFactory());
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
        EventLoopGroup clientGroup = new MultithreadEventLoopGroup(LocalHandler.newFactory());
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

    static Buffer createTestBuffer(int len) {
        Buffer buf = BufferAllocator.onHeapUnpooled().allocate(len);
        buf.writerOffset(len);
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

    void setInterest(LoggingHandler.Event... events) {
        loggingHandler.setInterest(events);
    }

}
