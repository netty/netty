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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * An encoder which serializes a Java object into a {@link ChannelBuffer}
 * (interoperability version).
 * <p>
 * This encoder is interoperable with the standard Java object streams such as
 * {@link ObjectInputStream} and {@link ObjectOutputStream}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev:231 $, $Date:2008-06-12 16:44:50 +0900 (목, 12 6월 2008) $
 */
@ChannelPipelineCoverage("one")
public class CompatibleObjectEncoder extends OneToOneEncoder {

    private final AtomicReference<ChannelBuffer> buffer =
        new AtomicReference<ChannelBuffer>();
    private final int resetInterval;
    private volatile ObjectOutputStream oout;
    private int writtenObjects;

    /**
     * Creates a new instance with the reset interval of {@code 16}.
     */
    public CompatibleObjectEncoder() {
        this(16); // Reset at every sixteen writes
    }

    /**
     * Creates a new instance.
     *
     * @param resetInterval
     *        the number of objects between {@link ObjectOutputStream#reset()}.
     *        {@code 0} will disable resetting the stream, but the remote
     *        peer will be at the risk of getting {@link OutOfMemoryError} in
     *        the long term.
     */
    public CompatibleObjectEncoder(int resetInterval) {
        if (resetInterval < 0) {
            throw new IllegalArgumentException(
                    "resetInterval: " + resetInterval);
        }
        this.resetInterval = resetInterval;
    }

    /**
     * Creates a new {@link ObjectOutputStream} which wraps the specified
     * {@link OutputStream}.  Override this method to use a subclass of the
     * {@link ObjectOutputStream}.
     */
    protected ObjectOutputStream newObjectOutputStream(OutputStream out) throws Exception {
        return new ObjectOutputStream(out);
    }

    @Override
    protected Object encode(ChannelHandlerContext context, Channel channel, Object msg) throws Exception {
        ChannelBuffer buffer = buffer(context);
        ObjectOutputStream oout = this.oout;
        if (resetInterval != 0) {
            // Resetting will prevent OOM on the receiving side.
            writtenObjects ++;
            if (writtenObjects % resetInterval == 0) {
                oout.reset();

                // Also discard the byproduct to avoid OOM on the sending side.
                buffer.discardReadBytes();
            }
        }
        oout.writeObject(msg);
        oout.flush();

        ChannelBuffer encoded = buffer.readBytes(buffer.readableBytes());
        return encoded;
    }

    private ChannelBuffer buffer(ChannelHandlerContext ctx) throws Exception {
        ChannelBuffer buf = buffer.get();
        if (buf == null) {
            ChannelBufferFactory factory = ctx.getChannel().getConfig().getBufferFactory();
            buf = ChannelBuffers.dynamicBuffer(factory);
            if (buffer.compareAndSet(null, buf)) {
                boolean success = false;
                try {
                    oout = newObjectOutputStream(new ChannelBufferOutputStream(buf));
                    success = true;
                } finally {
                    if (!success) {
                        oout = null;
                    }
                }
            } else {
                buf = buffer.get();
            }
        }
        return buf;
    }
}
