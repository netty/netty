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

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamConstants;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBufferInputStream;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.handler.codec.replay.ReplayingDecoder;
import net.gleamynode.netty.util.SwitchableInputStream;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class CompatibleObjectDecoder extends ReplayingDecoder<CompatibleObjectDecoderState> {

    private final SwitchableInputStream bin = new SwitchableInputStream();
    private volatile ObjectInputStream oin;

    public CompatibleObjectDecoder() {
        super(CompatibleObjectDecoderState.READ_HEADER);
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

    protected ObjectInputStream newObjectInputStream(InputStream in) throws Exception {
        return new ObjectInputStream(in);
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
