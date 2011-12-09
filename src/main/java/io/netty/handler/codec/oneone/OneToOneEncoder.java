/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.oneone;

import static io.netty.channel.Channels.*;

import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDownstreamHandler;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MessageEvent;
import io.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.frame.Delimiters;

/**
 * Transforms a write request into another write request.  A typical setup for
 * TCP/IP would be:
 * <pre>
 * {@link ChannelPipeline} pipeline = ...;
 *
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link DelimiterBasedFrameDecoder}(80, {@link Delimiters#nulDelimiter()}));
 * pipeline.addLast("customDecoder", new {@link OneToOneDecoder}() { ... });
 *
 * // Encoder
 * pipeline.addLast("customEncoder", new {@link OneToOneEncoder}() { ... });
 * </pre>
 * @apiviz.landmark
 */
public abstract class OneToOneEncoder implements ChannelDownstreamHandler {

    protected OneToOneEncoder() {
    }

    @Override
    public void handleDownstream(
            ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object originalMessage = e.getMessage();
        Object encodedMessage = encode(ctx, e.getChannel(), originalMessage);
        if (originalMessage == encodedMessage) {
            ctx.sendDownstream(evt);
        } else if (encodedMessage != null) {
            write(ctx, e.getFuture(), encodedMessage, e.getRemoteAddress());
        }
    }

    /**
     * Transforms the specified message into another message and return the
     * transformed message.  Note that you can not return {@code null}, unlike
     * you can in {@link OneToOneDecoder#decode(ChannelHandlerContext, Channel, Object)};
     * you must return something, at least {@link ChannelBuffers#EMPTY_BUFFER}.
     */
    protected abstract Object encode(
            ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception;
}
