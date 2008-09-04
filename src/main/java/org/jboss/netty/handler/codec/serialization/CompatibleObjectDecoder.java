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

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.util.SwitchableInputStream;

/**
 * A decoder which deserializes the received {@link ChannelBuffer}s into Java
 * objects (interoperability version).
 * <p>
 * This decoder is interoperable with the standard Java object
 * streams such as {@link ObjectInputStream} and {@link ObjectOutputStream}.
 * <p>
 * However, this decoder might perform worse than {@link ObjectDecoder} if
 * the serialized object is big and complex.  Also, it doesn't limit the
 * maximum size of the object, and consequently your application might face
 * the risk of <a href="http://en.wikipedia.org/wiki/DoS">DoS attack</a>.
 * Please use {@link ObjectEncoder} and {@link ObjectDecoder} if you are not
 * required to keep the interoperability with the standard object streams.
 *
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class CompatibleObjectDecoder extends ReplayingDecoder<CompatibleObjectDecoderState> {

    private final SwitchableInputStream bin = new SwitchableInputStream();
    private volatile ObjectInputStream oin;

    /**
     * Creates a new decoder.
     */
    public CompatibleObjectDecoder() {
        super(CompatibleObjectDecoderState.READ_HEADER);
    }

    /**
     * Creates a new {@link ObjectInputStream} which wraps the specified
     * {@link InputStream}.  Override this method to use a subclass of the
     * {@link ObjectInputStream}.
     */
    protected ObjectInputStream newObjectInputStream(InputStream in) throws Exception {
        return new ObjectInputStream(in);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, CompatibleObjectDecoderState state) throws Exception {
        bin.switchStream(new ChannelBufferInputStream(buffer));

        switch (state) {
        case READ_HEADER:
            oin = newObjectInputStream(bin);
            checkpoint(CompatibleObjectDecoderState.READ_OBJECT);
        case READ_OBJECT:
            return oin.readObject();
        default:
            throw new IllegalStateException("Unknown state: " + state);
        }
    }

    @Override
    protected Object decodeLast(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer, CompatibleObjectDecoderState state)
            throws Exception {
        // Ignore the last TC_RESET
        if (buffer.readableBytes() == 1 &&
            buffer.getByte(buffer.readerIndex()) == ObjectStreamConstants.TC_RESET) {
            buffer.skipBytes(1);
            oin.close();
            return null;
        }

        Object decoded =  decode(ctx, channel, buffer, state);
        oin.close();
        return decoded;
    }
}
