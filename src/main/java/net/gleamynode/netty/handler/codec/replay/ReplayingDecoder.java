/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.handler.codec.replay;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayBuffer;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ChannelUpstream;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
@PipelineCoverage("one")
public abstract class ReplayingDecoder extends ChannelEventHandlerAdapter {

    static final Error REWIND = new Rewind();

    private volatile ReplayableByteArrayBuffer cumulation = new ReplayableByteArrayBuffer();

    @Override
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) throws Exception {

        Object m = e.getMessage();
        if (!(m instanceof ByteArray)) {
            ctx.sendUpstream(e);
            return;
        }

        ByteArray input = (ByteArray) m;
        if (input.empty()) {
            return;
        }

        ReplayableByteArrayBuffer cumulation = this.cumulation;

        // Avoid CompositeByteArray index overflow.
        if (Integer.MAX_VALUE - cumulation.endIndex() < input.length()) {
            ReplayableByteArrayBuffer newCumulation = new ReplayableByteArrayBuffer();
            for (ByteArray component: cumulation) {
                newCumulation.unwrap().write(component);
            }
            this.cumulation = cumulation = newCumulation;
        }

        cumulation.unwrap().write(input);
        callDecode(ctx, e.getChannel(), cumulation);
    }

    @Override
    protected void channelDisconnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        cleanup(ctx, e);
    }

    @Override
    protected void exceptionCaught(
            PipeContext<ChannelEvent> ctx, ExceptionEvent e) throws Exception {
        ctx.sendUpstream(e);
    }

    private void callDecode(PipeContext<ChannelEvent> context,
            Channel channel, ReplayableByteArrayBuffer cumulation) throws Exception {
        while (!cumulation.empty()) {
            int oldFirstIndex = cumulation.unwrap().firstIndex();
            Object result = null;
            try {
                result = decode(context, channel, cumulation);
                // Successfully decoded a message; clean up recorded results.
                if (result != null) {
                    clear();
                } else if (oldFirstIndex == cumulation.unwrap().firstIndex()) {
                    throw new IllegalStateException(
                            "null cannot be returned if no data is consumed.");
                } else {
                    // Previous data has been discarded.
                    // Probably it's reading on.
                    clear();
                    continue;
                }
            } catch (Rewind rewind) {
                // Rewound
            }

            if (result == null) {
                // Seems like more data is required.
                // Let's wait for the next notification.
                break;
            }

            if (oldFirstIndex == cumulation.unwrap().firstIndex()) {
                throw new IllegalStateException(
                        "decode() method must consume at least one byte " +
                        "if it returned a decoded message.");
            }
            ChannelUpstream.fireMessageReceived(context, channel, result);
        }
    }

    private void cleanup(PipeContext<ChannelEvent> ctx, ChannelStateEvent e)
            throws Exception {
        // Make sure all frames were read before notifying a closed channel.
        callDecode(ctx, e.getChannel(), cumulation);
        ctx.sendUpstream(e);
    }

    protected void rewind() {
        cumulation.rewind();
    }

    protected void clear() {
        cumulation.clear();
    }

    protected abstract Object decode(
            PipeContext<ChannelEvent> ctx, Channel channel, ByteArrayBuffer buffer) throws Exception;
}
