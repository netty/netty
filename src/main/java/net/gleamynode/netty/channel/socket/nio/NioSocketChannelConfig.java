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
package net.gleamynode.netty.channel.socket.nio;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import net.gleamynode.netty.channel.socket.SocketChannelConfig;

/**
 *
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has net.gleamynode.netty.channel.socket.nio.ReceiveBufferSizePredictor
 */
public interface NioSocketChannelConfig extends SocketChannelConfig {

    int getWriteSpinCount();

    /**
     * The maximum loop count for a write operation until
     * {@link WritableByteChannel#write(ByteBuffer)} returns a non-zero value.
     * It is similar to what a spin lock is for in concurrency programming.
     * It improves memory utilization and write throughput significantly.
     */
    void setWriteSpinCount(int writeSpinCount);

    ReceiveBufferSizePredictor getReceiveBufferSizePredictor();
    void setReceiveBufferSizePredictor(ReceiveBufferSizePredictor predictor);

    boolean isReadWriteFair();
    void setReadWriteFair(boolean fair);
}
