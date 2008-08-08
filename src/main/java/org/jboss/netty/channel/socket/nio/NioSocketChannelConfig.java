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
package org.jboss.netty.channel.socket.nio;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.jboss.netty.channel.socket.SocketChannelConfig;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.socket.nio.ReceiveBufferSizePredictor
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
