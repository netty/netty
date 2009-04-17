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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * Encodes a {@link Number} into the binary representation with a 32-bit length
 * prefix.  For example, 42 will be encoded to { 0, 0, 0, 1, 42 }.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
@ChannelPipelineCoverage("all")
public class NumberEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(
            ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof Number)) {
            // Ignore what this encoder can't encode.
            return msg;
        }

        // Convert to a BigInteger first for easier implementation.
        BigInteger v;
        if (msg instanceof BigInteger) {
            v = (BigInteger) msg;
        } else {
            v = new BigInteger(String.valueOf(msg));
        }

        // Convert the number into a byte array.
        byte[] data = v.toByteArray();
        int dataLength = data.length;

        // Construct a message with a length header.
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer();
        buf.writeInt(dataLength);
        buf.writeBytes(data);

        // Return the constructed message.
        return buf;
    }
}
