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
package net.gleamynode.netty.handler.codec.serialization;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import net.gleamynode.netty.array.HeapByteArray;
import net.gleamynode.netty.channel.ChannelDownstream;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.pipeline.DownstreamHandler;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@PipelineCoverage("all")
public class ObjectEncoder implements DownstreamHandler<ChannelEvent> {
    // TODO Rewrite once gathering write is properly supported by JDK.
    //      See http://bugs.sun.com/view_bug.do?bug_id=6210541
    private static final byte[] LENGTH_PLACEHOLDER = new byte[4];

    private final int initialCapacity;

    public ObjectEncoder() {
        this(512);
    }

    public ObjectEncoder(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException(
                    "initialCapacity: " + initialCapacity);
        }
        this.initialCapacity = initialCapacity;
    }

    public void handleDownstream(
            PipeContext<ChannelEvent> context, ChannelEvent element) throws Exception {
        if (!(element instanceof MessageEvent)) {
            context.sendDownstream(element);
            return;
        }

        MessageEvent e = (MessageEvent) element;
        ByteArrayOutputStream bout = new ByteArrayOutputStream(initialCapacity);
        bout.write(LENGTH_PLACEHOLDER);
        ObjectOutputStream oout = new CompactObjectOutputStream(bout);
        oout.writeObject(e.getMessage());
        oout.flush();
        oout.close();

        byte[] msg = bout.toByteArray();
        int bodyLen = msg.length - 4;
        msg[0] = (byte) (bodyLen >>> 24);
        msg[1] = (byte) (bodyLen >>> 16);
        msg[2] = (byte) (bodyLen >>>  8);
        msg[3] = (byte) (bodyLen >>>  0);

        ChannelDownstream.write(
                context, e.getChannel(), e.getFuture(), new HeapByteArray(msg));
    }
}
