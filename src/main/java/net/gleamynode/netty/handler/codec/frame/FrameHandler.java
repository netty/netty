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
package net.gleamynode.netty.handler.codec.frame;

import net.gleamynode.netty.array.ByteArray;
import net.gleamynode.netty.array.ByteArrayBuffer;
import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ChannelUpstream;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
public abstract class FrameHandler extends ChannelEventHandlerAdapter {

    private volatile CompositeByteArray cumulation = new CompositeByteArray();

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

        CompositeByteArray cumulation = this.cumulation;

        // Avoid CompositeByteArray index overflow.
        if (Integer.MAX_VALUE - cumulation.endIndex() < input.length()) {
            CompositeByteArray newCumulation = new CompositeByteArray();
            for (ByteArray component: cumulation) {
                newCumulation.addLast(component);
            }
            this.cumulation = cumulation = newCumulation;
        }

        cumulation.addLast(input);
        callReadFrame(ctx, e.getChannel(), cumulation);
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

    protected abstract ByteArray readFrame(ByteArrayBuffer buffer) throws Exception;

    private void callReadFrame(PipeContext<ChannelEvent> context,
            Channel channel, CompositeByteArray cumulation) throws Exception {
        while (!cumulation.empty()) {
            int oldFirstIndex = cumulation.firstIndex();
            ByteArray frame = readFrame(cumulation);
            if (frame == null) {
                // Seems like more data is required.
                // Let's wait for the next notification.
                break;
            }
            if (oldFirstIndex == cumulation.firstIndex()) {
                throw new IllegalStateException(
                        "readFrame() method must consume at least one byte " +
                        "if it returned a frame.");
            }
            ChannelUpstream.fireMessageReceived(context, channel, frame);
        }
    }

    private void cleanup(PipeContext<ChannelEvent> ctx, ChannelStateEvent e)
            throws Exception {
        // Make sure all frames were read before notifying a closed channel.
        callReadFrame(ctx, e.getChannel(), cumulation);
        if (!cumulation.empty()) {
            // and send the remainders too if necessary.
            ChannelUpstream.fireMessageReceived(
                    ctx, e.getChannel(), cumulation.read(cumulation.length()));
        }
        ctx.sendUpstream(e);
    }
}
