/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 * A specialized version of {@link LengthFieldBasedFrameDecoder} which assumes
 * the length field is always located at offset {@code 0} and removes the length
 * field from the decoded frame.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class LengthPrefixedFrameDecoder extends LengthFieldBasedFrameDecoder {

    private final boolean stripLengthField;

    public LengthPrefixedFrameDecoder(int maxFrameLength, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldLength, 0, true);
    }

    public LengthPrefixedFrameDecoder(int maxFrameLength, int lengthFieldLength, boolean stripLengthField) {
        this(maxFrameLength, lengthFieldLength, 0, stripLengthField);
    }

    public LengthPrefixedFrameDecoder(int maxFrameLength, int lengthFieldLength, int lengthAdjustment) {
        this(maxFrameLength, lengthFieldLength, lengthAdjustment, true);
    }

    public LengthPrefixedFrameDecoder(int maxFrameLength, int lengthFieldLength, int lengthAdjustment, boolean stripLengthField) {
        super(maxFrameLength, 0, lengthFieldLength, lengthAdjustment);
        this.stripLengthField = stripLengthField;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel,
            ChannelBuffer buffer) throws Exception {
        ChannelBuffer frame = (ChannelBuffer) super.decode(ctx, channel, buffer);
        if (stripLengthField && frame != null) {
            frame.skipBytes(lengthFieldLength);
            return frame.slice();
        }

        return frame;
    }
}
