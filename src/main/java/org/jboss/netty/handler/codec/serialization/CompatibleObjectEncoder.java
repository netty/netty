/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.codec.serialization;

import static org.jboss.netty.buffer.ChannelBuffers.*;
import static org.jboss.netty.channel.Channels.*;

import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.MessageEvent;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 *
 */
@ChannelPipelineCoverage("all")
public class CompatibleObjectEncoder implements ChannelDownstreamHandler {

    private final ChannelBuffer buffer = dynamicBuffer();
    private final int resetInterval;
    private volatile ObjectOutputStream oout;
    private int writtenObjects;

    public CompatibleObjectEncoder() {
        this(16); // Reset at every sixteen writes
    }

    public CompatibleObjectEncoder(int resetInterval) {
        if (resetInterval < 0) {
            throw new IllegalArgumentException(
                    "resetInterval: " + resetInterval);
        }
        this.resetInterval = resetInterval;
    }

    public void handleDownstream(
            ChannelHandlerContext context, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            context.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;

        buffer.clear();
        if (oout == null) {
            oout = newObjectOutputStream(new ChannelBufferOutputStream(buffer));
        }

        if (resetInterval != 0) {
            // Resetting will prevent OOM on the receiving side.
            writtenObjects ++;
            if (writtenObjects % resetInterval == 0) {
                oout.reset();
            }
        }
        oout.writeObject(e.getMessage());
        oout.flush();

        ChannelBuffer encoded = buffer.readBytes(buffer.readableBytes());
        write(context, e.getChannel(), e.getFuture(), encoded, e.getRemoteAddress());
    }

    protected ObjectOutputStream newObjectOutputStream(OutputStream out) throws Exception {
        return new ObjectOutputStream(out);
    }
}
