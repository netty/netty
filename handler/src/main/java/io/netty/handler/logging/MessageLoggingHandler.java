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
package io.netty.handler.logging;

import io.netty.buffer.BufUtil;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandler;
import io.netty.channel.ChannelOutboundMessageHandler;
import io.netty.channel.ChannelPromise;

public class MessageLoggingHandler
        extends LoggingHandler
        implements ChannelInboundMessageHandler<Object>, ChannelOutboundMessageHandler<Object> {

    public MessageLoggingHandler() { }

    public MessageLoggingHandler(Class<?> clazz, LogLevel level) {
        super(clazz, level);
    }

    public MessageLoggingHandler(Class<?> clazz) {
        super(clazz);
    }

    public MessageLoggingHandler(LogLevel level) {
        super(level);
    }

    public MessageLoggingHandler(String name, LogLevel level) {
        super(name, level);
    }

    public MessageLoggingHandler(String name) {
        super(name);
    }

    @Override
    public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public MessageBuf<Object> newOutboundBuffer(ChannelHandlerContext ctx) throws Exception {
        return Unpooled.messageBuffer();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        MessageBuf<Object> buf = ctx.inboundMessageBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("RECEIVED", buf)));
        }

        MessageBuf<Object> out = ctx.nextInboundMessageBuffer();
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            out.add(o);
        }
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        MessageBuf<Object> buf = ctx.outboundMessageBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("WRITE", buf)));
        }

        MessageBuf<Object> out = ctx.nextOutboundMessageBuffer();
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            out.add(o);
        }
        ctx.flush(promise);
    }

    protected String formatBuffer(String message, MessageBuf<Object> buf) {
        return message + '(' + buf.size() + "): " + BufUtil.contentToString(buf);
    }
}
