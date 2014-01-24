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
package io.netty.example.spdyclient;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.spdy.SpdyFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * Logs SPDY frames for debugging purposes.
 */
public class SpdyFrameLogger extends MessageToMessageCodec<SpdyFrame, SpdyFrame> {

    private enum Direction {
        INBOUND, OUTBOUND
    }

    protected final InternalLogger logger;
    private final InternalLogLevel level;

    public SpdyFrameLogger(InternalLogLevel level) {
        if (level == null) {
            throw new NullPointerException("level");
        }

        this.logger = InternalLoggerFactory.getInstance(getClass());
        this.level = level;
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof SpdyFrame;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof SpdyFrame;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, SpdyFrame msg, List<Object> out) throws Exception {
        log(msg, Direction.OUTBOUND);
        out.add(ReferenceCountUtil.retain(msg));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, SpdyFrame msg, List<Object> out) throws Exception {
        log(msg, Direction.INBOUND);
        out.add(ReferenceCountUtil.retain(msg));
    }

    private void log(SpdyFrame msg, Direction d) {
        if (logger.isEnabled(this.level)) {
            StringBuilder b = new StringBuilder("\n----------------").append(d.name()).append("--------------------\n");
            b.append(msg.toString());
            b.append("\n------------------------------------");
            logger.log(this.level, b.toString());
        }
    }
}
