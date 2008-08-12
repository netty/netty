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
package org.jboss.netty.example.factorial;

import java.math.BigInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * Decodes the binary representation of a {@link BigInteger} with 32-bit
 * integer length prefix into a Java {@link BigInteger} instance.  For example,
 * { 0, 0, 0, 1, 42 } will be decoded into new BigInteger("42").
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class BigIntegerDecoder extends FrameDecoder {

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        // Wait until the length prefix is available.
        if (buffer.readableBytes() < 4) {
            return null;
        }
        int dataLength = buffer.getInt(buffer.readerIndex());

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + 4) {
            return null;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(4);

        // Convert the received data into a new BigInteger.
        byte[] decoded = new byte[dataLength];
        buffer.readBytes(decoded);

        return new BigInteger(decoded);
    }
}
