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
package io.netty.example.spdy.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.spdy.SpdyFrame;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Logs SPDY frames for debugging purposes.
 */
public class SpdyFrameLogger extends ChannelDuplexHandler {

    private enum Direction {
        INBOUND, OUTBOUND
    }

    protected final InternalLogger logger;
    private final InternalLogLevel level;

    public SpdyFrameLogger(InternalLogLevel level) {
        this.level = ObjectUtil.checkNotNull(level, "level");
        this.logger = InternalLoggerFactory.getInstance(getClass());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (acceptMessage(msg)) {
            log((SpdyFrame) msg, Direction.INBOUND);
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (acceptMessage(msg)) {
            log((SpdyFrame) msg, Direction.OUTBOUND);
        }
        ctx.write(msg, promise);
    }

    private static boolean acceptMessage(Object msg) {
        return msg instanceof SpdyFrame;
    }

    private void log(SpdyFrame msg, Direction d) {
        if (logger.isEnabled(level)) {
            StringBuilder b = new StringBuilder(200)
                .append("\n----------------")
                .append(d.name())
                .append("--------------------\n")
                .append(msg)
                .append("\n------------------------------------");

            logger.log(level, b.toString());
        }
    }
}
