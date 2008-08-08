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

import java.io.StreamCorruptedException;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.buffer.ChannelBufferInputStream;
import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelHandlerContext;
import net.gleamynode.netty.handler.codec.frame.FrameDecoder;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ObjectDecoder extends FrameDecoder {

    private final int maxObjectSize;
    private final ClassLoader classLoader;

    public ObjectDecoder() {
        this(1048576);
    }

    public ObjectDecoder(int maxObjectSize) {
        this(maxObjectSize, Thread.currentThread().getContextClassLoader());
    }

    public ObjectDecoder(int maxObjectSize, ClassLoader classLoader) {
        if (maxObjectSize <= 0) {
            throw new IllegalArgumentException("maxObjectSize: " + maxObjectSize);
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }

        this.maxObjectSize = maxObjectSize;
        this.classLoader = classLoader;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        if (buffer.readableBytes() < 4) {
            return null;
        }

        int dataLen = buffer.getInt(buffer.readerIndex());
        if (dataLen <= 0) {
            throw new StreamCorruptedException("invalid data length: " + dataLen);
        }
        if (dataLen > maxObjectSize) {
            throw new StreamCorruptedException(
                    "data length too big: " + dataLen + " (max: " + maxObjectSize + ')');
        }

        if (buffer.readableBytes() < dataLen + 4) {
            return null;
        }

        buffer.skipBytes(4);
        return new CompactObjectInputStream(
                new ChannelBufferInputStream(buffer, dataLen),
                classLoader).readObject();
    }
}
