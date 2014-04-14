/*
 * Copyright 2014 The Netty Project
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
package io.netty.example.http2.client;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.draft10.frame.Http2Frame;
import io.netty.handler.codec.http2.draft10.frame.Http2SettingsFrame;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
public class Http2FrameLogger extends ChannelHandlerAdapter {

    private enum Direction {
        INBOUND, OUTBOUND
    }

    protected final InternalLogger logger;
    private final InternalLogLevel level;

    public Http2FrameLogger(InternalLogLevel level) {
        if (level == null) {
            throw new NullPointerException("level");
        }

        logger = InternalLoggerFactory.getInstance(getClass());
        this.level = level;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (acceptMessage(msg)) {
            log((Http2Frame) msg, Direction.INBOUND);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (acceptMessage(msg)) {
            if (msg instanceof Http2SettingsFrame) {
              Thread.sleep(100);
            }
            log((Http2Frame) msg, Direction.OUTBOUND);
        }
        super.write(ctx, msg, promise);
    }

    private static boolean acceptMessage(Object msg) throws Exception {
        return msg instanceof Http2Frame;
    }

    private void log(Http2Frame msg, Direction d) {
        if (logger.isEnabled(level)) {
            StringBuilder b = new StringBuilder("\n----------------");
            b.append(d.name());
            b.append("--------------------\n");
            b.append(msg);
            b.append("\n------------------------------------");
            logger.log(level, b.toString());
        }
    }
}
