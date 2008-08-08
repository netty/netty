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
package net.gleamynode.netty.example.factorial;

import java.math.BigInteger;

import net.gleamynode.netty.array.CompositeByteArray;
import net.gleamynode.netty.channel.ChannelDownstream;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipeHandlerAdapter;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class NumberEncoder extends PipeHandlerAdapter<ChannelEvent> {

    @Override
    public void handleDownstream(
            PipeContext<ChannelEvent> ctx, ChannelEvent element) throws Exception {
        if (!(element instanceof MessageEvent)) {
            ctx.sendDownstream(element);
            return;
        }

        MessageEvent e = (MessageEvent) element;
        if (!(e.getMessage() instanceof Number)) {
            // Ignore what this encoder can't encode.
            ctx.sendDownstream(element);
            return;
        }

        // Convert to a BigInteger first for easier implementation.
        BigInteger v;
        if (e.getMessage() instanceof BigInteger) {
            v = (BigInteger) e.getMessage();
        } else {
            v = new BigInteger(e.getMessage().toString());
        }

        // Convert the number into a byte array.
        byte[] data = v.toByteArray();
        int dataLength = data.length;

        // Construct a message with a length header.
        CompositeByteArray buf = new CompositeByteArray(8);
        buf.writeBE32(dataLength);
        buf.write(data);

        // Send the constructed message.
        ChannelDownstream.write(
                ctx, e.getChannel(), e.getFuture(), buf, e.getRemoteAddress());
    }
}
