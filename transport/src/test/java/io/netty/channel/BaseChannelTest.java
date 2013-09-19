/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;


import static org.junit.Assert.assertEquals;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;

import java.io.UnsupportedEncodingException;

class BaseChannelTest {

    private final LoggingHandler loggingHandler;

    BaseChannelTest() {
        this.loggingHandler = new LoggingHandler();
    }

    ServerBootstrap getLocalServerBootstrap() {
        EventLoopGroup serverGroup = new LocalEventLoopGroup();
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
        EventLoopGroup clientGroup = new LocalEventLoopGroup();
        Bootstrap cb = new Bootstrap();
        cb.channel(LocalChannel.class);
        cb.group(clientGroup);

        cb.handler(this.loggingHandler);

        return cb;
    }

    static ByteBuf createTestBuf(int len) {
        ByteBuf buf = Unpooled.buffer(len, len);
        buf.setIndex(0, len);
        return buf;
    }

    static Object createTestBuf(String string) throws UnsupportedEncodingException {
        byte[] buf = string.getBytes("US-ASCII");
        return createTestBuf(buf);
    }

    static Object createTestBuf(byte[] buf) {
        ByteBuf ret = createTestBuf(buf.length);
        ret.clear();
        ret.writeBytes(buf);
        return ret;
    }

    void assertLog(String expected) {
        String actual = this.loggingHandler.getLog();
        assertEquals(expected, actual);
    }

    void clearLog() {
        this.loggingHandler.clear();
    }

    void setInterest(LoggingHandler.Event... events) {
        this.loggingHandler.setInterest(events);
    }

}
